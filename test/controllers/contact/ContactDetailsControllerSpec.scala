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

package controllers.contact

import base.SpecBase
import models.ConsentStatus.Granted
import models.{CompanyInformation, NotificationEmail, UserDetails}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import play.api.{Application, inject}
import services.TradeReportingExtractsService
import uk.gov.hmrc.http.HeaderCarrier
import views.html.contact.ContactDetailsView

import java.time.LocalDateTime
import scala.concurrent.Future

class ContactDetailsControllerSpec extends SpecBase {

  "ContactDetailsController" - {

    val manageEmailGuideUrl: String =
      "https://www.gov.uk/guidance/manage-your-email-address-for-the-customs-declaration-service"

    "must return OK and render the correct view for a GET request" in new Setup {

      running(application) {

        val userDetails = UserDetails(
          eori = eori,
          additionalEmails = Seq("test@example.com"),
          authorisedUsers = Seq.empty,
          companyInformation = companyInformation,
          notificationEmail = NotificationEmail("notify@example.com", LocalDateTime.now(), false)
        )

        when(mockService.getUserDetails(any[String])(any[HeaderCarrier]))
          .thenReturn(Future.successful(userDetails))

        val request = FakeRequest(GET, controllers.contact.routes.ContactDetailsController.onPageLoad().url)
        val result  = route(application, request).value

        val view = application.injector.instanceOf[ContactDetailsView]

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(
          companyInformation,
          eori,
          "notify@example.com",
          Seq("test@example.com"),
          manageEmailGuideUrl,
          false,
          false
        )(
          request,
          messages(application)
        ).toString
      }
    }

    "must return OK and render the correct view for when a user has no valid email address" in new Setup {

      running(application) {

        val userDetails = UserDetails(
          eori = eori,
          additionalEmails = Seq("test@example.com"),
          authorisedUsers = Seq.empty,
          companyInformation = companyInformation,
          notificationEmail = NotificationEmail("notify@example.com", LocalDateTime.now(), true)
        )

        when(mockService.getUserDetails(any[String])(any[HeaderCarrier]))
          .thenReturn(Future.successful(userDetails))

        val request = FakeRequest(GET, controllers.contact.routes.ContactDetailsController.onPageLoad().url)
        val result  = route(application, request).value

        val view = application.injector.instanceOf[ContactDetailsView]

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(
          companyInformation,
          eori,
          "notify@example.com",
          Seq("test@example.com"),
          manageEmailGuideUrl,
          false,
          true
        )(
          request,
          messages(application)
        ).toString
        contentAsString(result) must include(
          "We are unable to display your registered email address because there is no verified email address."
        )
      }
    }

    "must return OK and render the correct view for when a user has an inactive eori" in new Setup {

      running(application) {

        val userDetails = UserDetails(
          eori = eori,
          additionalEmails = Seq("test@example.com"),
          authorisedUsers = Seq.empty,
          companyInformation = companyInformation.copy(inactiveEori = true),
          notificationEmail = NotificationEmail("notify@example.com", LocalDateTime.now(), false)
        )

        when(mockService.getUserDetails(any[String])(any[HeaderCarrier]))
          .thenReturn(Future.successful(userDetails))

        val request = FakeRequest(GET, controllers.contact.routes.ContactDetailsController.onPageLoad().url)
        val result  = route(application, request).value

        val view = application.injector.instanceOf[ContactDetailsView]

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(
          companyInformation,
          eori,
          "notify@example.com",
          Seq("test@example.com"),
          manageEmailGuideUrl,
          true,
          false
        )(
          request,
          messages(application)
        ).toString
        contentAsString(result) must include("Your EORI number is inactive")
      }
    }

    "must return OK and render the correct view for when a user has an inactive eori and no available email address" in new Setup {

      running(application) {

        val userDetails = UserDetails(
          eori = eori,
          additionalEmails = Seq("test@example.com"),
          authorisedUsers = Seq.empty,
          companyInformation = companyInformation.copy(inactiveEori = true),
          notificationEmail = NotificationEmail("notify@example.com", LocalDateTime.now(), true)
        )

        when(mockService.getUserDetails(any[String])(any[HeaderCarrier]))
          .thenReturn(Future.successful(userDetails))

        val request = FakeRequest(GET, controllers.contact.routes.ContactDetailsController.onPageLoad().url)
        val result  = route(application, request).value

        val view = application.injector.instanceOf[ContactDetailsView]

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(
          companyInformation,
          eori,
          "notify@example.com",
          Seq("test@example.com"),
          manageEmailGuideUrl,
          true,
          true
        )(
          request,
          messages(application)
        ).toString
        contentAsString(result) must include("Your EORI number is inactive")
      }
    }

    "must return OK and render the correct view for a GET request when user has reached additional email limit of 5" in new Setup {

      private val existingEmails = Seq(
        "existing1@example.com",
        "existing2@example.com",
        "existing3@example.com",
        "existing4@example.com",
        "existing5@example.com"
      )

      running(application) {

        val userDetails = UserDetails(
          eori = eori,
          additionalEmails = existingEmails,
          authorisedUsers = Seq.empty,
          companyInformation = companyInformation,
          notificationEmail = NotificationEmail("notify@example.com", LocalDateTime.now(), false)
        )

        when(mockService.getUserDetails(any[String])(any[HeaderCarrier]))
          .thenReturn(Future.successful(userDetails))

        val request = FakeRequest(GET, controllers.contact.routes.ContactDetailsController.onPageLoad().url)
        val result  = route(application, request).value

        val view = application.injector.instanceOf[ContactDetailsView]

        status(result) mustEqual OK
        val body = contentAsString(result)
        val msgs = messages(application)
        body mustEqual view(
          companyInformation,
          eori,
          "notify@example.com",
          existingEmails,
          manageEmailGuideUrl,
          false,
          false
        )(
          request,
          msgs
        ).toString

        body must include(msgs("additionalEmail.limitReached"))
        val addUrl = controllers.contact.routes.NewAdditionalEmailController.onPageLoad().url
        body must not include addUrl

      }
    }
  }

  trait Setup {
    val mockService: TradeReportingExtractsService = mock[TradeReportingExtractsService]

    val companyInformation: CompanyInformation =
      CompanyInformation(
        name = "ABC Company",
        consent = Granted,
        inactiveEori = false
      )

    val eori: String = "GB123456789002"

    val application: Application = applicationBuilder()
      .overrides(
        inject.bind[TradeReportingExtractsService].toInstance(mockService)
      )
      .configure("features.new-agent-view-enabled" -> false)
      .build()
  }
}
