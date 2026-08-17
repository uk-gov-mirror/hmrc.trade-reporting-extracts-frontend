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
import forms.thirdparty.ConfirmEoriFormProvider
import models.thirdparty.ConfirmEori
import models.{CompanyInformation, ConsentStatus, NormalMode, UserAnswers}
import navigation.{FakeNavigator, Navigator}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import pages.thirdparty.{ConfirmEoriPage, EoriNumberPage}
import play.api.inject.bind
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import repositories.SessionRepository
import services.TradeReportingExtractsService
import views.html.thirdparty.ConfirmEoriView

import scala.concurrent.Future

class ConfirmEoriControllerSpec extends SpecBase with MockitoSugar {

  def onwardRoute: Call                = Call("GET", "/request-customs-declaration-data/reference-name")
  def onwardRouteSkipRefNamePage: Call = Call("GET", "/request-customs-declaration-data/access-start-date")

  lazy val confirmEoriRoute: String =
    controllers.thirdparty.routes.ConfirmEoriController.onPageLoad(NormalMode).url

  val formProvider = new ConfirmEoriFormProvider()
  val form         = formProvider()

  val mockTradeReportingExtractsService: TradeReportingExtractsService = mock[TradeReportingExtractsService]
  val mockSessionRepository: SessionRepository                         = mock[SessionRepository]

  val eoriNumber                                 = "GB123456789000"
  val companyInfo: CompanyInformation            = CompanyInformation("Test Company", ConsentStatus.Denied, false)
  val companyInfoWithConsent: CompanyInformation = CompanyInformation("Test Company", ConsentStatus.Granted, false)

  "ConfirmEori Controller" - {

    "must return OK and the correct view for a GET" in {
      when(mockTradeReportingExtractsService.getCompanyInformation(any())(any()))
        .thenReturn(Future.successful(companyInfo))

      val userAnswers = emptyUserAnswers
        .set(EoriNumberPage, "GB123456789000")
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          bind[TradeReportingExtractsService].toInstance(mockTradeReportingExtractsService)
        )
        .build()

      running(application) {
        val request = FakeRequest(GET, confirmEoriRoute)

        val result = route(application, request).value
        val view   = application.injector.instanceOf[ConfirmEoriView]

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(
          form,
          NormalMode,
          eoriNumber,
          messages(application)("confirmEori.noConsent")
        )(request, messages(application)).toString
      }
    }

    "must populate the view correctly on a GET when the question has previously been answered" in {
      val userAnswers = UserAnswers(userAnswersId)
        .set(ConfirmEoriPage, ConfirmEori.Yes)
        .success
        .value
        .set(EoriNumberPage, eoriNumber)
        .success
        .value

      when(mockTradeReportingExtractsService.getCompanyInformation(any())(any()))
        .thenReturn(Future.successful(companyInfo))

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          bind[TradeReportingExtractsService].toInstance(mockTradeReportingExtractsService)
        )
        .build()

      running(application) {
        val request = FakeRequest(GET, confirmEoriRoute)
        val view    = application.injector.instanceOf[ConfirmEoriView]

        val result = route(application, request).value

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(
          form.fill(ConfirmEori.Yes),
          NormalMode,
          eoriNumber,
          messages(application)("confirmEori.noConsent")
        )(request, messages(application)).toString
      }
    }

    "must redirect to the next page when valid data is submitted" in {
      when(mockSessionRepository.set(any())).thenReturn(Future.successful(true))
      when(mockTradeReportingExtractsService.getCompanyInformation(any())(any()))
        .thenReturn(Future.successful(companyInfo))

      val userAnswers = UserAnswers(userAnswersId)
        .set(EoriNumberPage, eoriNumber)
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          bind[Navigator].toInstance(new FakeNavigator(onwardRoute)),
          bind[SessionRepository].toInstance(mockSessionRepository),
          bind[TradeReportingExtractsService].toInstance(mockTradeReportingExtractsService)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, confirmEoriRoute)
          .withFormUrlEncodedBody("value" -> ConfirmEori.Yes.toString)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual onwardRoute.url
      }
    }

    "must redirect to ThirdPartyAccessStartDate page when No consent in company info" in {
      when(mockSessionRepository.set(any())).thenReturn(Future.successful(true))
      when(mockTradeReportingExtractsService.getCompanyInformation(any())(any()))
        .thenReturn(Future.successful(companyInfoWithConsent))

      val userAnswers = UserAnswers(userAnswersId)
        .set(EoriNumberPage, eoriNumber)
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          bind[Navigator].toInstance(new FakeNavigator(onwardRoute)),
          bind[SessionRepository].toInstance(mockSessionRepository),
          bind[TradeReportingExtractsService].toInstance(mockTradeReportingExtractsService)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, confirmEoriRoute)
          .withFormUrlEncodedBody("value" -> ConfirmEori.Yes.toString)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual onwardRouteSkipRefNamePage.url
      }
    }

    "must return a Bad Request and errors when invalid data is submitted" in {
      when(mockTradeReportingExtractsService.getCompanyInformation(any())(any()))
        .thenReturn(Future.successful(companyInfo))

      val userAnswers = UserAnswers(userAnswersId)
        .set(EoriNumberPage, eoriNumber)
        .success
        .value
      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          bind[TradeReportingExtractsService].toInstance(mockTradeReportingExtractsService)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, confirmEoriRoute)
          .withFormUrlEncodedBody("value" -> "invalid value")

        val boundForm = form.bind(Map("value" -> "invalid value"))
        val view      = application.injector.instanceOf[ConfirmEoriView]

        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        contentAsString(result) mustEqual view(
          boundForm,
          NormalMode,
          eoriNumber,
          messages(application)("confirmEori.noConsent")
        )(
          request,
          messages(application)
        ).toString
      }
    }

    "must redirect to Journey Recovery for a GET if no existing data is found" in {
      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request = FakeRequest(GET, confirmEoriRoute)
        val result  = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual controllers.problem.routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must redirect to Journey Recovery for a POST if no existing data is found" in {
      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request = FakeRequest(POST, confirmEoriRoute)
          .withFormUrlEncodedBody("value" -> ConfirmEori.Yes.toString)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual controllers.problem.routes.JourneyRecoveryController.onPageLoad().url
      }
    }
  }
}
