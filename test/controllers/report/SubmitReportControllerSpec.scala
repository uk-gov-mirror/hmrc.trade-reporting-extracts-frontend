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
import exceptions.NoAuthorisedUserFoundException
import models.report.ChooseEori.{Myauthority, Myeori}
import models.report.{ReportConfirmation, ReportRequestUserAnswersModel, SubmissionMeta}
import models.{AlreadySubmittedFlag, NotificationEmail, ThirdPartyDetails, UserAnswers}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{any, argThat}
import org.mockito.Mockito.{verify, when}
import org.scalatestplus.mockito.MockitoSugar.mock
import pages.report.{ChooseEoriPage, SelectThirdPartyEoriPage}
import play.api.inject
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import repositories.SessionRepository
import services.{ReportRequestDataService, TradeReportingExtractsService}

import java.time.*
import scala.concurrent.Future

class SubmitReportControllerSpec extends SpecBase {

  "SubmitReportController" - {

    "must redirect to confirmation page and update session on successful submission" in {
      val mockSessionRepository             = mock[SessionRepository]
      val mockTradeReportingExtractsService = mock[TradeReportingExtractsService]
      val mockReportRequestDataService      = mock[ReportRequestDataService]

      val userAnswers       = emptyUserAnswers.set(ChooseEoriPage, Myeori).success.value
      val notificationEmail = NotificationEmail("test@example.com", LocalDateTime.now(), false)

      when(mockTradeReportingExtractsService.getNotificationEmail(any())(any()))
        .thenReturn(Future.successful(notificationEmail))

      when(mockTradeReportingExtractsService.createReportRequest(any())(any()))
        .thenReturn(Future.successful(Seq(ReportConfirmation("MyReport", "importHeader", "Reference"))))

      when(mockReportRequestDataService.buildReportRequest(any(), any()))
        .thenReturn(
          Some(
            ReportRequestUserAnswersModel(
              eori = "GB123456789000",
              dataType = "exports",
              whichEori = "GB987654321000",
              eoriRole = Set("declarant"),
              reportType = Set("summary"),
              reportStartDate = "2025-01-01",
              reportEndDate = "2025-12-31",
              reportName = "Test Report",
              additionalEmail = Some(Set("notify@example.com"))
            )
          )
        )

      when(mockSessionRepository.set(any()))
        .thenReturn(Future.successful(true))

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          inject.bind[SessionRepository].toInstance(mockSessionRepository),
          inject.bind[TradeReportingExtractsService].toInstance(mockTradeReportingExtractsService),
          inject.bind[ReportRequestDataService].toInstance(mockReportRequestDataService)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, controllers.report.routes.SubmitReportController.onSubmit.url)
        val result  = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual controllers.report.routes.RequestConfirmationController
          .onPageLoad()
          .url
      }
    }

    "must redirect to report request issue page when buildReportRequest returns None" in {
      val mockSessionRepository             = mock[SessionRepository]
      val mockTradeReportingExtractsService = mock[TradeReportingExtractsService]
      val mockReportRequestDataService      = mock[ReportRequestDataService]

      val userAnswers = emptyUserAnswers.set(ChooseEoriPage, Myeori).success.value

      when(mockReportRequestDataService.buildReportRequest(any(), any()))
        .thenReturn(None)

      when(mockSessionRepository.set(any()))
        .thenReturn(Future.successful(true))

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          inject.bind[SessionRepository].toInstance(mockSessionRepository),
          inject.bind[TradeReportingExtractsService].toInstance(mockTradeReportingExtractsService),
          inject.bind[ReportRequestDataService].toInstance(mockReportRequestDataService)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, controllers.report.routes.SubmitReportController.onSubmit.url)
        val result  = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual controllers.problem.routes.ReportRequestIssueController
          .onPageLoad()
          .url
      }
    }

    "must create SubmissionMeta with correct values and save to session repository" in {
      val mockSessionRepository             = mock[SessionRepository]
      val mockTradeReportingExtractsService = mock[TradeReportingExtractsService]
      val mockReportRequestDataService      = mock[ReportRequestDataService]

      val userAnswers         = emptyUserAnswers.set(ChooseEoriPage, Myeori).success.value
      val notificationEmail   = NotificationEmail("test@example.com", LocalDateTime.now(), false)
      val reportConfirmations = Seq(ReportConfirmation("MyReport", "importHeader", "Reference"))

      val fixedInstant = Instant.parse("2025-06-01T12:00:00Z")
      val fixedClock   = Clock.fixed(fixedInstant, ZoneOffset.UTC)

      when(mockTradeReportingExtractsService.getNotificationEmail(any())(any()))
        .thenReturn(Future.successful(notificationEmail))

      when(mockTradeReportingExtractsService.createReportRequest(any())(any()))
        .thenReturn(Future.successful(reportConfirmations))

      when(mockReportRequestDataService.buildReportRequest(any(), any()))
        .thenReturn(
          Some(
            ReportRequestUserAnswersModel(
              eori = "GB123456789000",
              dataType = "exports",
              whichEori = "GB987654321000",
              eoriRole = Set("declarant"),
              reportType = Set("summary"),
              reportStartDate = "2025-01-01",
              reportEndDate = "2025-12-31",
              reportName = "Test Report",
              additionalEmail = Some(Set("notify@example.com"))
            )
          )
        )

      when(mockSessionRepository.set(any()))
        .thenReturn(Future.successful(true))

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          inject.bind[SessionRepository].toInstance(mockSessionRepository),
          inject.bind[TradeReportingExtractsService].toInstance(mockTradeReportingExtractsService),
          inject.bind[ReportRequestDataService].toInstance(mockReportRequestDataService),
          inject.bind[Clock].toInstance(fixedClock)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, controllers.report.routes.SubmitReportController.onSubmit.url)
        val result  = route(application, request).value

        status(result) mustEqual SEE_OTHER

        val expectedSubmissionMeta = Json
          .toJson(
            SubmissionMeta(
              reportConfirmations = reportConfirmations,
              submittedAt = fixedInstant,
              isMoreThanOneReport = false,
              allEmails = Seq(notificationEmail.address)
            )
          )
          .as[JsObject]

        verify(mockSessionRepository).set(argThat { userAnswers =>
          userAnswers.submissionMeta.contains(expectedSubmissionMeta) &&
          userAnswers.get(AlreadySubmittedFlag()).contains(true)
        })
      }
    }

    "must fail with RuntimeException when service fails" in {
      val mockTradeReportingExtractsService = mock[TradeReportingExtractsService]
      val mockReportRequestDataService      = mock[ReportRequestDataService]
      val mockSessionRepository             = mock[SessionRepository]

      when(mockTradeReportingExtractsService.getNotificationEmail(any())(any()))
        .thenReturn(Future.failed(new RuntimeException("Service error")))

      val userAnswers = emptyUserAnswers.set(ChooseEoriPage, Myeori).success.value

      when(mockReportRequestDataService.buildReportRequest(any(), any()))
        .thenReturn(
          Some(
            ReportRequestUserAnswersModel(
              eori = "GB123456789000",
              dataType = "exports",
              whichEori = "GB987654321000",
              eoriRole = Set("declarant"),
              reportType = Set("summary"),
              reportStartDate = "2025-01-01",
              reportEndDate = "2025-12-31",
              reportName = "Test Report",
              additionalEmail = Some(Set("notify@example.com"))
            )
          )
        )

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          inject.bind[SessionRepository].toInstance(mockSessionRepository),
          inject.bind[TradeReportingExtractsService].toInstance(mockTradeReportingExtractsService),
          inject.bind[ReportRequestDataService].toInstance(mockReportRequestDataService)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, controllers.report.routes.SubmitReportController.onSubmit.url)
        val result  = route(application, request).value

        whenReady(result.failed) { ex =>
          ex mustBe a[RuntimeException]
          ex.getMessage mustEqual "Service error"
        }
      }
    }

    "must collect and store additional emails in SubmissionMeta" in {
      val mockSessionRepository             = mock[SessionRepository]
      val mockTradeReportingExtractsService = mock[TradeReportingExtractsService]
      val mockReportRequestDataService      = mock[ReportRequestDataService]

      val notificationEmail   = NotificationEmail("primary@example.com", LocalDateTime.now(), false)
      val reportConfirmations = Seq(ReportConfirmation("MyReport", "importHeader", "Reference"))

      val fixedInstant = Instant.parse("2025-06-01T12:00:00Z")
      val fixedClock   = Clock.fixed(fixedInstant, ZoneOffset.UTC)

      val userAnswers = emptyUserAnswers
        .set(ChooseEoriPage, Myeori)
        .success
        .value
        .set(
          pages.report.EmailSelectionPage,
          Set("existing@example.com", models.report.EmailSelection.AddNewEmailValue)
        )
        .success
        .value
        .set(pages.report.NewEmailNotificationPage, "new@example.com")
        .success
        .value

      when(mockTradeReportingExtractsService.getNotificationEmail(any())(any()))
        .thenReturn(Future.successful(notificationEmail))

      when(mockTradeReportingExtractsService.createReportRequest(any())(any()))
        .thenReturn(Future.successful(reportConfirmations))

      when(mockReportRequestDataService.buildReportRequest(any(), any()))
        .thenReturn(
          Some(
            ReportRequestUserAnswersModel(
              eori = "GB123456789000",
              dataType = "exports",
              whichEori = "GB987654321000",
              eoriRole = Set("declarant"),
              reportType = Set("summary"),
              reportStartDate = "2025-01-01",
              reportEndDate = "2025-12-31",
              reportName = "Test Report",
              additionalEmail = None
            )
          )
        )

      when(mockSessionRepository.set(any()))
        .thenReturn(Future.successful(true))

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          inject.bind[SessionRepository].toInstance(mockSessionRepository),
          inject.bind[TradeReportingExtractsService].toInstance(mockTradeReportingExtractsService),
          inject.bind[ReportRequestDataService].toInstance(mockReportRequestDataService),
          inject.bind[Clock].toInstance(fixedClock)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, controllers.report.routes.SubmitReportController.onSubmit.url)
        val result  = route(application, request).value

        status(result) mustEqual SEE_OTHER

        val expectedEmails         = Seq("primary@example.com", "existing@example.com", "new@example.com")
        val expectedSubmissionMeta = Json
          .toJson(
            SubmissionMeta(
              reportConfirmations = reportConfirmations,
              submittedAt = fixedInstant,
              isMoreThanOneReport = false,
              allEmails = expectedEmails
            )
          )
          .as[JsObject]

        verify(mockSessionRepository).set(argThat { userAnswers =>
          userAnswers.submissionMeta.contains(expectedSubmissionMeta) &&
          userAnswers.get(AlreadySubmittedFlag()).contains(true)
        })
      }
    }
  }

  "removal of third party access handling scenarions" - {

    "when third party journey and a trader eori selected must submit report when third party access still available" in {

      val mockSessionRepository             = mock[SessionRepository]
      val mockTradeReportingExtractsService = mock[TradeReportingExtractsService]
      val mockReportRequestDataService      = mock[ReportRequestDataService]

      val userAnswers = emptyUserAnswers
        .set(ChooseEoriPage, Myauthority)
        .success
        .value
        .set(SelectThirdPartyEoriPage, "foo")
        .success
        .value

      when(mockReportRequestDataService.buildReportRequest(any(), any()))
        .thenReturn(None)

      when(mockSessionRepository.set(any()))
        .thenReturn(Future.successful(true))

      when(mockTradeReportingExtractsService.getAuthorisedBusinessDetails(any(), any())(any()))
        .thenReturn(Future.successful(ThirdPartyDetails(None, LocalDate.now(), None, Set("imports"), None, None)))

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          inject.bind[SessionRepository].toInstance(mockSessionRepository),
          inject.bind[TradeReportingExtractsService].toInstance(mockTradeReportingExtractsService),
          inject.bind[ReportRequestDataService].toInstance(mockReportRequestDataService)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, controllers.report.routes.SubmitReportController.onSubmit.url)
        val result  = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual controllers.problem.routes.ReportRequestIssueController
          .onPageLoad()
          .url
      }
    }

    "when third party journey and trader eori selected must handle failure if third party access removed" in {

      val mockSessionRepository             = mock[SessionRepository]
      val mockTradeReportingExtractsService = mock[TradeReportingExtractsService]
      val mockReportRequestDataService      = mock[ReportRequestDataService]
      val userAnswersCaptor                 = ArgumentCaptor.forClass(classOf[UserAnswers])

      val userAnswers = emptyUserAnswers
        .set(ChooseEoriPage, Myauthority)
        .success
        .value
        .set(SelectThirdPartyEoriPage, "foo")
        .success
        .value

      when(mockReportRequestDataService.buildReportRequest(any(), any()))
        .thenReturn(None)

      when(mockSessionRepository.set(userAnswersCaptor.capture())) thenReturn Future.successful(true)

      when(mockTradeReportingExtractsService.getAuthorisedBusinessDetails(any(), any())(any()))
        .thenReturn(Future.failed(new NoAuthorisedUserFoundException("No authorised user found")))

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          inject.bind[SessionRepository].toInstance(mockSessionRepository),
          inject.bind[TradeReportingExtractsService].toInstance(mockTradeReportingExtractsService),
          inject.bind[ReportRequestDataService].toInstance(mockReportRequestDataService)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, controllers.report.routes.SubmitReportController.onSubmit.url)
        val result  = route(application, request).value

        status(result) mustEqual SEE_OTHER
        val capturedAnswers = userAnswersCaptor.getValue
        capturedAnswers.get(ChooseEoriPage) mustBe None
        capturedAnswers.get(SelectThirdPartyEoriPage) mustBe None
        redirectLocation(result).value mustEqual controllers.report.routes.RequestNotCompletedController
          .onPageLoad("foo")
          .url
      }
    }

    "in any other scenario, remove all user answers and reset the journey" in {

      val mockSessionRepository             = mock[SessionRepository]
      val mockTradeReportingExtractsService = mock[TradeReportingExtractsService]
      val mockReportRequestDataService      = mock[ReportRequestDataService]
      val userAnswersCaptor                 = ArgumentCaptor.forClass(classOf[UserAnswers])

      val userAnswers = emptyUserAnswers
        .set(ChooseEoriPage, Myauthority)
        .success
        .value

      when(mockReportRequestDataService.buildReportRequest(any(), any()))
        .thenReturn(None)

      when(mockSessionRepository.set(userAnswersCaptor.capture())) thenReturn Future.successful(true)

      when(mockTradeReportingExtractsService.getAuthorisedBusinessDetails(any(), any())(any()))
        .thenReturn(Future.failed(new NoAuthorisedUserFoundException("No authorised user found")))

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          inject.bind[SessionRepository].toInstance(mockSessionRepository),
          inject.bind[TradeReportingExtractsService].toInstance(mockTradeReportingExtractsService),
          inject.bind[ReportRequestDataService].toInstance(mockReportRequestDataService)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, controllers.report.routes.SubmitReportController.onSubmit.url)
        val result  = route(application, request).value

        status(result) mustEqual SEE_OTHER
        val capturedAnswers = userAnswersCaptor.getValue
        capturedAnswers.get(ChooseEoriPage) mustBe None
        redirectLocation(result).value mustEqual controllers.problem.routes.ReportRequestIssueController
          .onPageLoad()
          .url
      }
    }
  }
}
