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

package controllers.report

import base.SpecBase
import forms.report.MaybeAdditionalEmailFormProvider
import models.report.ReportTypeImport
import models.{NormalMode, NotificationEmail, UserAnswers}
import navigation.{FakeNavigator, Navigator}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import pages.report.{MaybeAdditionalEmailPage, ReportTypeImportPage}
import play.api.inject.bind
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import repositories.SessionRepository
import services.TradeReportingExtractsService
import views.html.report.MaybeAdditionalEmailView

import java.time.LocalDateTime
import scala.concurrent.Future

class MaybeAdditionalEmailControllerSpec extends SpecBase with MockitoSugar {

  def onwardRoute: Call = Call("GET", "/request-customs-declaration-data/notification-email")

  val formProvider                      = new MaybeAdditionalEmailFormProvider()
  val form                              = formProvider()
  val mockTradeReportingExtractsService = mock[TradeReportingExtractsService]

  lazy val maybeAdditionalEmailRoute =
    controllers.report.routes.MaybeAdditionalEmailController.onPageLoad(NormalMode).url

  "MaybeAdditionalEmail Controller" - {

    "must return OK and the correct view for a GET" in {

      when(mockTradeReportingExtractsService.getNotificationEmail(any())(any()))
        .thenReturn(Future.successful(NotificationEmail("test@email.com", LocalDateTime.now(), false)))

      val application = applicationBuilder(userAnswers =
        Some(
          emptyUserAnswers
            .set(ReportTypeImportPage, Set(ReportTypeImport.ImportHeader, ReportTypeImport.ImportItem))
            .success
            .value
        )
      )
        .overrides(
          bind[TradeReportingExtractsService].toInstance(mockTradeReportingExtractsService)
        )
        .build()

      running(application) {
        val request = FakeRequest(GET, maybeAdditionalEmailRoute)

        val result = route(application, request).value

        val view = application.injector.instanceOf[MaybeAdditionalEmailView]

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(form, NormalMode, true, "test@email.com")(
          request,
          messages(application)
        ).toString
      }
    }

    "must populate the view correctly on a GET when the question has previously been answered" in {

      when(mockTradeReportingExtractsService.getNotificationEmail(any())(any()))
        .thenReturn(Future.successful(NotificationEmail("test@email.com", LocalDateTime.now(), false)))

      val userAnswers = UserAnswers(userAnswersId)
        .set(MaybeAdditionalEmailPage, true)
        .success
        .value
        .set(ReportTypeImportPage, Set(ReportTypeImport.ImportHeader))
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          bind[TradeReportingExtractsService].toInstance(mockTradeReportingExtractsService)
        )
        .build()

      running(application) {
        val request = FakeRequest(GET, maybeAdditionalEmailRoute)

        val view = application.injector.instanceOf[MaybeAdditionalEmailView]

        val result = route(application, request).value

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(form.fill(true), NormalMode, false, "test@email.com")(
          request,
          messages(application)
        ).toString
      }
    }

    "must redirect to the next page when valid data is submitted" in {

      val mockSessionRepository = mock[SessionRepository]

      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

      when(mockTradeReportingExtractsService.getNotificationEmail(any())(any()))
        .thenReturn(Future.successful(NotificationEmail("test@email.com", LocalDateTime.now(), false)))

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
          FakeRequest(POST, maybeAdditionalEmailRoute)
            .withFormUrlEncodedBody(("value", "true"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
      }
    }

    "must return a Bad Request and errors when invalid data is submitted" in {

      when(mockTradeReportingExtractsService.getNotificationEmail(any())(any()))
        .thenReturn(Future.successful(NotificationEmail("test@email.com", LocalDateTime.now(), false)))

      val application = applicationBuilder(userAnswers =
        Some(
          emptyUserAnswers
            .set(ReportTypeImportPage, Set(ReportTypeImport.ImportHeader, ReportTypeImport.ImportItem))
            .success
            .value
        )
      )
        .overrides(
          bind[TradeReportingExtractsService].toInstance(mockTradeReportingExtractsService)
        )
        .build()

      running(application) {
        val request =
          FakeRequest(POST, maybeAdditionalEmailRoute)
            .withFormUrlEncodedBody(("value", ""))

        val boundForm = form.bind(Map("value" -> ""))

        val view = application.injector.instanceOf[MaybeAdditionalEmailView]

        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        contentAsString(result) mustEqual view(boundForm, NormalMode, true, "test@email.com")(
          request,
          messages(application)
        ).toString
      }
    }

    "must redirect to Journey Recovery for a GET if no existing data is found" in {

      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request = FakeRequest(GET, maybeAdditionalEmailRoute)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual controllers.problem.routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must redirect to Journey Recovery for a POST if no existing data is found" in {

      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request =
          FakeRequest(POST, maybeAdditionalEmailRoute)
            .withFormUrlEncodedBody(("value", "true"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual controllers.problem.routes.JourneyRecoveryController.onPageLoad().url
      }
    }
  }
}
