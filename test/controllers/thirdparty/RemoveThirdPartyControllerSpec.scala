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
import forms.thirdparty.RemoveThirdPartyFormProvider
import models.NotificationEmail
import org.apache.pekko.Done
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.data.Form
import play.api.inject
import play.api.inject.bind
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import repositories.SessionRepository
import services.TradeReportingExtractsService
import views.html.thirdparty.RemoveThirdPartyView

import java.time.LocalDateTime
import scala.concurrent.Future

class RemoveThirdPartyControllerSpec extends SpecBase with MockitoSugar {

  val formProvider        = new RemoveThirdPartyFormProvider()
  val form: Form[Boolean] = formProvider()

  lazy val removeThirdPartyRoute: String =
    controllers.thirdparty.routes.RemoveThirdPartyController.onPageLoad("Eori").url

  "RemoveThirdParty Controller" - {

    "must return OK and the correct view for a GET" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, removeThirdPartyRoute)

        val result = route(application, request).value

        val view = application.injector.instanceOf[RemoveThirdPartyView]

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(form, "Eori")(request, messages(application)).toString
      }
    }

    "must redirect to the next page when valid data is submitted" in {

      val mockSessionRepository             = mock[SessionRepository]
      val mockTradeReportingExtractsService = mock[TradeReportingExtractsService]
      val notificationEmail                 = NotificationEmail("test@example.com", LocalDateTime.now(), false)

      when(mockTradeReportingExtractsService.getNotificationEmail(any())(any()))
        .thenReturn(Future.successful(notificationEmail))

      when(mockTradeReportingExtractsService.removeThirdParty(any(), any())(any()))
        .thenReturn(Future.successful(Done))

      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

      val application =
        applicationBuilder(userAnswers = Some(emptyUserAnswers))
          .overrides(
            bind[SessionRepository].toInstance(mockSessionRepository),
            bind[TradeReportingExtractsService].toInstance(mockTradeReportingExtractsService)
          )
          .build()

      running(application) {
        val request =
          FakeRequest(POST, removeThirdPartyRoute)
            .withFormUrlEncodedBody(("value", "true"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(
          result
        ).value mustEqual controllers.thirdparty.routes.RemoveThirdPartyConfirmationController.onPageLoad.url
      }
    }

    "must return a Bad Request and errors when invalid data is submitted" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request =
          FakeRequest(POST, removeThirdPartyRoute)
            .withFormUrlEncodedBody(("value", ""))

        val boundForm = form.bind(Map("value" -> ""))

        val view = application.injector.instanceOf[RemoveThirdPartyView]

        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        contentAsString(result) mustEqual view(boundForm, "Eori")(request, messages(application)).toString
      }
    }
  }
}
