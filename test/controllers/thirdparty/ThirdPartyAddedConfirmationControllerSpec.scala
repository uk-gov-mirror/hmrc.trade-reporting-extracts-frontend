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
import config.FrontendAppConfig
import models.thirdparty.ThirdPartySubmissionMeta
import models.UserAnswers
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import views.html.thirdparty.ThirdPartyAddedConfirmationView

class ThirdPartyAddedConfirmationControllerSpec extends SpecBase with MockitoSugar {

  "ThirdPartyAddedConfirmationController" - {

    "return OK and render the view with submissionMeta details when present" in {
      val submissionMeta = ThirdPartySubmissionMeta(
        thirdPartyEori = "GB123456123456",
        companyName = Some("Test Company"),
        submittedDate = "5 May 2025"
      )

      val userAnswers: UserAnswers = emptyUserAnswers.copy(
        submissionMeta = Some(Json.toJson(submissionMeta).as[JsObject])
      )

      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, routes.ThirdPartyAddedConfirmationController.onPageLoad().url)
        val result  = route(application, request).value

        val view      = application.injector.instanceOf[ThirdPartyAddedConfirmationView]
        val appConfig = application.injector.instanceOf[FrontendAppConfig]

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(
          "GB123456123456",
          "5 May 2025",
          appConfig.exitSurveyUrl
        )(request, messages(application)).toString
      }
    }

    "return OK and render the view with empty details when submissionMeta is missing" in {
      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, routes.ThirdPartyAddedConfirmationController.onPageLoad().url)
        val result  = route(application, request).value

        val view      = application.injector.instanceOf[ThirdPartyAddedConfirmationView]
        val appConfig = application.injector.instanceOf[FrontendAppConfig]

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(
          "",
          "",
          appConfig.exitSurveyUrl
        )(request, messages(application)).toString
      }
    }
  }
}
