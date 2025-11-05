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
import config.FrontendAppConfig
import models.report.{EmailSelection, ReportConfirmation, SubmissionMeta}
import pages.report.{EmailSelectionPage, NewEmailNotificationPage}
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import utils.DateTimeFormats.formattedSystemTime
import views.html.report.RequestConfirmationView

import java.time.{Clock, Instant, ZoneOffset}
import play.api.i18n.Lang
import play.api.inject

class RequestConfirmationControllerSpec extends SpecBase {

  private val fixedClock: Clock = Clock.fixed(Instant.parse("2025-05-05T10:15:30Z"), ZoneOffset.UTC)

  "RequestConfirmationController" - {

    "return OK and render the correct view when submissionMeta and EmailSelectionPage are defined" in {
      val newEmail          = "new.email@example.com"
      val selectedEmails    = Seq("email1@example.com", "email2@example.com", newEmail)
      val emailString       = selectedEmails.mkString(", ")
      val notificationEmail = "notify@example.com"

      val submissionMetaJson = Json.toJson(
        SubmissionMeta(
          reportConfirmations = Seq(ReportConfirmation("MyReport", "importTaxLine", "RE00000001")),
          notificationEmail = notificationEmail,
          submittedDate = "5 May 2025",
          submittedTime = formattedSystemTime(fixedClock)(Lang("en"))
        )
      )

      val userAnswers = emptyUserAnswers
        .set(EmailSelectionPage, selectedEmails.toSet)
        .success
        .value
        .set(NewEmailNotificationPage, newEmail)
        .success
        .value
        .copy(submissionMeta = Some(submissionMetaJson.as[JsObject]))

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(inject.bind[Clock].toInstance(fixedClock))
        .build()

      running(application) {
        val appConfig = application.injector.instanceOf[FrontendAppConfig]
        val surveyUrl = appConfig.exitSurveyUrl
        val view      = application.injector.instanceOf[RequestConfirmationView]

        val request = FakeRequest(GET, controllers.report.routes.RequestConfirmationController.onPageLoad().url)
        val result  = route(application, request).value

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(
          Some(emailString),
          false,
          Seq(ReportConfirmation("MyReport", "reportTypeImport.importTaxLine", "RE00000001")),
          surveyUrl,
          notificationEmail,
          "5 May 2025",
          formattedSystemTime(fixedClock)(Lang("en"))
        )(request, messages(application)).toString

        contentAsString(result) must include("MyReport")
        contentAsString(result) must include("RE00000001")
        contentAsString(result) must include(notificationEmail)
      }
    }

    "return OK and render empty values when submissionMeta is missing" in {
      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, controllers.report.routes.RequestConfirmationController.onPageLoad().url)
        val result  = route(application, request).value

        status(result) mustEqual OK
        contentAsString(result) must include("")
      }
    }
  }
}
