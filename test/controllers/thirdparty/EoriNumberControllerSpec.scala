/*
 * Copyright 2025 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers.thirdparty

import base.SpecBase
import forms.thirdparty.EoriNumberFormProvider
import models.ConsentStatus.{Denied, Granted}
import models.thirdparty.{ConfirmEori, DataTypes, DeclarationDate}
import models.{CompanyInformation, NormalMode, UserAnswers}
import navigation.{FakeNavigator, FakeReportNavigator, Navigator}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import pages.thirdparty.*
import play.api.inject.bind
import play.api.libs.json.Reads
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import repositories.SessionRepository
import services.TradeReportingExtractsService
import views.html.thirdparty.EoriNumberView

import java.time.LocalDate
import scala.concurrent.Future

class EoriNumberControllerSpec extends SpecBase with MockitoSugar {

  def onwardRoute: Call = Call("GET", "/request-customs-declaration-data/confirm-eori")

  val userEori = "GB123456789000"

  lazy val eoriNumberRoute = controllers.thirdparty.routes.EoriNumberController.onPageLoad(NormalMode).url

  implicit val localDateReads: Reads[LocalDate]               = Reads.localDateReads("yyyy-MM-dd")
  implicit val optionLocalDateReads: Reads[Option[LocalDate]] = Reads.optionWithNull[LocalDate]

  "EoriNumber Controller" - {

    "must return OK and the correct view for a GET" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, eoriNumberRoute)

        val result = route(application, request).value

        val view = application.injector.instanceOf[EoriNumberView]
        val form = new EoriNumberFormProvider().apply(userEori)

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(form, NormalMode)(request, messages(application)).toString
      }
    }

    "must populate the view correctly on a GET when the question has previously been answered" in {

      val mockTradeReportingExtractsService = mock[TradeReportingExtractsService]
      val companyInfo                       = CompanyInformation(name = "Test", consent = Granted, inactiveEori = false)

      when(mockTradeReportingExtractsService.getCompanyInformation(any())(any()))
        .thenReturn(Future.successful(companyInfo))

      val userAnswers = UserAnswers(userAnswersId).set(EoriNumberPage, "answer").success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(bind[TradeReportingExtractsService].toInstance(mockTradeReportingExtractsService))
        .build()

      running(application) {
        val request = FakeRequest(GET, eoriNumberRoute)

        val view = application.injector.instanceOf[EoriNumberView]
        val form = new EoriNumberFormProvider().apply(userEori)

        val result = route(application, request).value

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(form.fill("answer"), NormalMode)(request, messages(application)).toString
      }
    }

    "must redirect to the next page when valid data is submitted" in {

      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

      val mockTradeReportingExtractsService = mock[TradeReportingExtractsService]
      val companyInfo                       = CompanyInformation(name = "Test", consent = Denied, inactiveEori = false)

      when(mockTradeReportingExtractsService.getCompanyInformation(any())(any()))
        .thenReturn(Future.successful(companyInfo))

      when(mockTradeReportingExtractsService.getAuthorisedEoris(any())(any()))
        .thenReturn(Future.successful(Seq("GB123456123447")))

      val application =
        applicationBuilder(userAnswers = Some(emptyUserAnswers))
          .overrides(
            bind[Navigator].toInstance(new FakeNavigator(onwardRoute)),
            bind[SessionRepository].toInstance(mockSessionRepository),
            bind[TradeReportingExtractsService].toInstance(mockTradeReportingExtractsService)
          )
          .build()

      running(application) {
        val request =
          FakeRequest(POST, eoriNumberRoute)
            .withFormUrlEncodedBody(("value", "GB123456123456"))

        val result = route(application, request).value
        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual onwardRoute.url
      }
    }

    "must return a Bad Request and errors when invalid data is submitted" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request =
          FakeRequest(POST, eoriNumberRoute)
            .withFormUrlEncodedBody(("value", ""))
        val form    = new EoriNumberFormProvider().apply(userEori)

        val boundForm = form.bind(Map("value" -> ""))

        val view = application.injector.instanceOf[EoriNumberView]

        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        contentAsString(result) mustEqual view(boundForm, NormalMode)(request, messages(application)).toString
      }
    }

    "must redirect to Journey Recovery for a GET if no existing data is found" in {

      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request = FakeRequest(GET, eoriNumberRoute)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual controllers.problem.routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must redirect to Journey Recovery for a POST if no existing data is found" in {

      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request =
          FakeRequest(POST, eoriNumberRoute)
            .withFormUrlEncodedBody(("value", "answer"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual controllers.problem.routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "if user changes the eori to a new value it must clear any data and redirect to next page" in {
      val oldEori = "GB123456123491"
      val newEori = "GB123456123499"

      val mockSessionRepository = mock[SessionRepository]
      val userAnswersCaptor     = ArgumentCaptor.forClass(classOf[UserAnswers])

      when(mockSessionRepository.set(userAnswersCaptor.capture())) thenReturn Future.successful(true)

      val mockTradeReportingExtractsService = mock[TradeReportingExtractsService]
      val companyInfo                       = CompanyInformation(name = "Test", consent = Denied, inactiveEori = false)

      when(mockTradeReportingExtractsService.getCompanyInformation(any())(any()))
        .thenReturn(Future.successful(companyInfo))

      when(mockTradeReportingExtractsService.getAuthorisedEoris(any())(any()))
        .thenReturn(Future.successful(Seq("GB123456123447")))

      val userAnswers = UserAnswers(userAnswersId)
        .set(ThirdPartyDataOwnerConsentPage, true)
        .success
        .value
        .set(EoriNumberPage, oldEori)
        .success
        .value
        .set(ConfirmEoriPage, ConfirmEori.Yes)
        .success
        .value
        .set(ThirdPartyReferencePage, "thirdPartyRef")
        .success
        .value
        .set(ThirdPartyAccessStartDatePage, LocalDate.now().minusDays(10))
        .success
        .value
        .set(ThirdPartyAccessEndDatePage, Some(LocalDate.now().minusDays(5)))
        .success
        .value
        .set(DataTypesPage, Set(DataTypes.Import))
        .success
        .value
        .set(DeclarationDatePage, DeclarationDate.CustomDateRange)
        .success
        .value
        .set(DataStartDatePage, LocalDate.now().minusDays(10))
        .success
        .value
        .set(DataEndDatePage, Some(LocalDate.now().minusDays(5)))
        .success
        .value

      val application =
        applicationBuilder(userAnswers = Some(userAnswers))
          .overrides(
            bind[Navigator].toInstance(new FakeReportNavigator(onwardRoute)),
            bind[SessionRepository].toInstance(mockSessionRepository),
            bind[TradeReportingExtractsService].toInstance(mockTradeReportingExtractsService)
          )
          .build()

      running(application) {
        val request =
          FakeRequest(POST, eoriNumberRoute)
            .withFormUrlEncodedBody(("value", newEori))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        val capturedUserAnswers = userAnswersCaptor.getValue
        capturedUserAnswers.get(ConfirmEoriPage) mustBe None
        capturedUserAnswers.get(ThirdPartyReferencePage) mustBe None
        capturedUserAnswers.get(ThirdPartyAccessStartDatePage) mustBe None
        capturedUserAnswers.get(ThirdPartyAccessEndDatePage) mustBe None
        capturedUserAnswers.get(DataTypesPage) mustBe None
        capturedUserAnswers.get(DeclarationDatePage) mustBe None
        capturedUserAnswers.get(DataEndDatePage) mustBe None
        capturedUserAnswers.get(ThirdPartyDataOwnerConsentPage) mustBe Some(true)
      }
    }

    "if the user does not change the eori value, must not clear any data and redirect to next page" in {
      val sameEori = "GB123456123491"

      val mockSessionRepository = mock[SessionRepository]
      val userAnswersCaptor     = ArgumentCaptor.forClass(classOf[UserAnswers])

      when(mockSessionRepository.set(userAnswersCaptor.capture())) thenReturn Future.successful(true)

      val mockTradeReportingExtractsService = mock[TradeReportingExtractsService]
      val companyInfo                       = CompanyInformation(name = "Test", consent = Denied, inactiveEori = false)

      when(mockTradeReportingExtractsService.getCompanyInformation(any())(any()))
        .thenReturn(Future.successful(companyInfo))

      when(mockTradeReportingExtractsService.getAuthorisedEoris(any())(any()))
        .thenReturn(Future.successful(Seq("GB123456123447")))

      val userAnswers = UserAnswers(userAnswersId)
        .set(EoriNumberPage, sameEori)
        .success
        .value
        .set(DataStartDatePage, LocalDate.now().minusDays(10))
        .success
        .value

      val application =
        applicationBuilder(userAnswers = Some(userAnswers))
          .overrides(
            bind[Navigator].toInstance(new FakeReportNavigator(onwardRoute)),
            bind[SessionRepository].toInstance(mockSessionRepository),
            bind[TradeReportingExtractsService].toInstance(mockTradeReportingExtractsService)
          )
          .build()

      running(application) {
        val request =
          FakeRequest(POST, eoriNumberRoute)
            .withFormUrlEncodedBody(("value", sameEori))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        val capturedUserAnswers = userAnswersCaptor.getValue
        capturedUserAnswers.get(DataStartDatePage) mustBe Some(LocalDate.now().minusDays(10))
      }
    }

    "if the user enters eori number that has third party access already, redirect user to EoriNumberLreadyAdded Page" in {
      val eori = "GB123456123491"

      val mockSessionRepository = mock[SessionRepository]
      val userAnswersCaptor     = ArgumentCaptor.forClass(classOf[UserAnswers])

      when(mockSessionRepository.set(userAnswersCaptor.capture())) thenReturn Future.successful(true)

      val mockTradeReportingExtractsService = mock[TradeReportingExtractsService]
      val companyInfo                       = CompanyInformation(name = "Test", consent = Denied, inactiveEori = false)

      when(mockTradeReportingExtractsService.getCompanyInformation(any())(any()))
        .thenReturn(Future.successful(companyInfo))

      when(mockTradeReportingExtractsService.getAuthorisedEoris(any())(any()))
        .thenReturn(Future.successful(Seq("GB123456123491")))

      val userAnswers = UserAnswers(userAnswersId)
        .set(EoriNumberPage, eori)
        .success
        .value
        .set(EoriAlreadyAddedPage, eori)
        .success
        .value

      val application =
        applicationBuilder(userAnswers = Some(userAnswers))
          .overrides(
            bind[Navigator].toInstance(new FakeReportNavigator(onwardRoute)),
            bind[SessionRepository].toInstance(mockSessionRepository),
            bind[TradeReportingExtractsService].toInstance(mockTradeReportingExtractsService)
          )
          .build()

      running(application) {
        val request =
          FakeRequest(POST, eoriNumberRoute)
            .withFormUrlEncodedBody(("value", eori))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual controllers.thirdparty.routes.EoriAlreadyAddedController
          .onPageLoad()
          .url
      }
    }
  }
}
