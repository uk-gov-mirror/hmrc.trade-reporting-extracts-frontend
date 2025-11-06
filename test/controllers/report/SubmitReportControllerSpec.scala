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
import models.NotificationEmail
import models.report.{ReportConfirmation, ReportRequestUserAnswersModel}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.inject
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import repositories.SessionRepository
import services.{ReportRequestDataService, TradeReportingExtractsService}

import java.time.LocalDateTime
import scala.concurrent.Future

class SubmitReportControllerSpec extends SpecBase {

  "SubmitReportController" - {

    "must redirect to confirmation page and update session on successful submission" in {
      val mockSessionRepository             = mock[SessionRepository]
      val mockTradeReportingExtractsService = mock[TradeReportingExtractsService]
      val mockReportRequestDataService      = mock[ReportRequestDataService]

      val userAnswers       = emptyUserAnswers
      val notificationEmail = NotificationEmail("test@example.com", LocalDateTime.now())

      when(mockTradeReportingExtractsService.getNotificationEmail(any())(any()))
        .thenReturn(Future.successful(notificationEmail))

      when(mockTradeReportingExtractsService.createReportRequest(any())(any()))
        .thenReturn(Future.successful(Seq(ReportConfirmation("MyReport", "importHeader", "Reference"))))

      when(mockReportRequestDataService.buildReportRequest(any(), any()))
        .thenReturn(
          ReportRequestUserAnswersModel(
            eori = "GB123456789000",
            dataType = "exports",
            whichEori = Some("GB987654321000"),
            eoriRole = Set("declarant"),
            reportType = Set("summary"),
            reportStartDate = "2025-01-01",
            reportEndDate = "2025-12-31",
            reportName = "Test Report",
            additionalEmail = Some(Set("notify@example.com"))
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

    "must fail with RuntimeException when service fails" in {
      val mockTradeReportingExtractsService = mock[TradeReportingExtractsService]
      val mockReportRequestDataService      = mock[ReportRequestDataService]
      val mockSessionRepository             = mock[SessionRepository]

      when(mockTradeReportingExtractsService.getNotificationEmail(any())(any()))
        .thenReturn(Future.failed(new RuntimeException("Service error")))

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
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
  }
}
