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
import models.thirdparty.*
import models.{CompanyInformation, ConsentStatus}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.mockito.MockitoSugar.mock
import pages.thirdparty.*
import play.api.inject.bind
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import services.{AuditService, ThirdPartyService, TradeReportingExtractsService}
import viewmodels.checkAnswers.thirdparty.{BusinessInfoSummary, EoriNumberSummary, ThirdPartyReferenceSummary}
import viewmodels.govuk.all.SummaryListViewModel
import views.html.thirdparty.AddThirdPartyCheckYourAnswersView

import java.time.{Clock, Instant, LocalDate, ZoneOffset}
import scala.concurrent.Future

class AddThirdPartyCheckYourAnswersControllerSpec extends SpecBase with MockitoSugar {

  private val fixedClock: Clock                                        = Clock.fixed(Instant.parse("2025-05-05T10:15:30Z"), ZoneOffset.UTC)
  val mockTradeReportingExtractsService: TradeReportingExtractsService = mock[TradeReportingExtractsService]
  val mockAuditService: AuditService                                   = mock[AuditService]
  val mockThirdPartyService: ThirdPartyService                         = mock[ThirdPartyService]

  "AddThirdPartyCheckYourAnswers Controller" - {

    "must return OK and the correct view for a GET when business consent given" in {

      val userAnswers = emptyUserAnswers
        .set(EoriNumberPage, "GB123456789000")
        .success
        .value

      when(mockTradeReportingExtractsService.getCompanyInformation(any())(any()))
        .thenReturn(Future.successful(CompanyInformation("businessInfo", ConsentStatus.Granted)))

      val application =
        applicationBuilder(userAnswers = Some(userAnswers))
          .overrides(
            bind[TradeReportingExtractsService].toInstance(mockTradeReportingExtractsService)
          )
          .build()

      running(application) {
        val request = FakeRequest(GET, routes.AddThirdPartyCheckYourAnswersController.onPageLoad().url)
        val result  = route(application, request).value
        val view    = application.injector.instanceOf[AddThirdPartyCheckYourAnswersView]

        val list = SummaryListViewModel(
          Seq(
            EoriNumberSummary.checkYourAnswersRow(userAnswers)(messages(application)).get,
            BusinessInfoSummary.row("businessInfo")(messages(application)).get
          )
        )

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(list)(request, messages(application)).toString
      }
    }

    "must return OK and the correct view for a GET when business consent not given" in {

      val userAnswers = emptyUserAnswers
        .set(EoriNumberPage, "GB123456789000")
        .success
        .value
        .set(ThirdPartyReferencePage, "ref")
        .success
        .value

      when(mockTradeReportingExtractsService.getCompanyInformation(any())(any()))
        .thenReturn(Future.successful(CompanyInformation("businessInfo", ConsentStatus.Denied)))

      val application =
        applicationBuilder(userAnswers = Some(userAnswers))
          .overrides(
            bind[TradeReportingExtractsService].toInstance(mockTradeReportingExtractsService)
          )
          .build()

      running(application) {
        val request = FakeRequest(GET, routes.AddThirdPartyCheckYourAnswersController.onPageLoad().url)
        val result  = route(application, request).value
        val view    = application.injector.instanceOf[AddThirdPartyCheckYourAnswersView]

        val list = SummaryListViewModel(
          Seq(
            EoriNumberSummary.checkYourAnswersRow(userAnswers)(messages(application)).get,
            ThirdPartyReferenceSummary.checkYourAnswersRow(userAnswers)(messages(application)).get
          )
        )

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(list)(request, messages(application)).toString
      }
    }

    "must redirect to confirmation page for a POST" in {
      val userAnswers = emptyUserAnswers
        .set(ThirdPartyDataOwnerConsentPage, true)
        .success
        .value
        .set(EoriNumberPage, "GB123456789000")
        .success
        .value
        .set(ConfirmEoriPage, ConfirmEori.Yes)
        .success
        .value
        .set(ThirdPartyReferencePage, "ref")
        .success
        .value
        .set(ThirdPartyAccessStartDatePage, LocalDate.of(2025, 1, 1))
        .success
        .value
        .set(ThirdPartyAccessEndDatePage, Some(LocalDate.of(2025, 1, 1)))
        .success
        .value
        .set(DataTypesPage, Set(DataTypes.Export))
        .success
        .value
        .set(DeclarationDatePage, DeclarationDate.CustomDateRange)
        .success
        .value
        .set(DataStartDatePage, LocalDate.of(2025, 1, 1))
        .success
        .value
        .set(DataEndDatePage, Some(LocalDate.of(2025, 1, 1)))
        .success
        .value

      when(mockThirdPartyService.buildThirdPartyAddRequest(any(), any())).thenReturn(
        ThirdPartyRequest(
          userEORI = "GB987654321098",
          thirdPartyEORI = "GB123456123456",
          accessStart = Instant.parse("2025-09-09T00:00:00Z"),
          accessEnd = Some(Instant.parse("2025-09-09T10:59:38.334682780Z")),
          reportDateStart = Some(Instant.parse("2025-09-10T00:00:00Z")),
          reportDateEnd = Some(Instant.parse("2025-09-09T10:59:38.334716742Z")),
          accessType = Set("IMPORT", "EXPORT"),
          referenceName = Some("TestReport")
        )
      )

      when(mockTradeReportingExtractsService.createThirdPartyAddRequest(any())(any()))
        .thenReturn(Future.successful(ThirdPartyAddedConfirmation(thirdPartyEori = "GB123456123456")))

      when(mockTradeReportingExtractsService.getCompanyInformation(any())(any()))
        .thenReturn(Future.successful(CompanyInformation("businessInfo", ConsentStatus.Granted)))
      when(mockAuditService.auditThirdPartyAdded(any())(any())).thenReturn(Future.successful(()))

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          bind[TradeReportingExtractsService].toInstance(mockTradeReportingExtractsService),
          bind[Clock].toInstance(fixedClock)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.AddThirdPartyCheckYourAnswersController.onSubmit().url)
        val result  = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual controllers.thirdparty.routes.ThirdPartyAddedConfirmationController
          .onPageLoad()
          .url
      }
    }

    "must return correct details for audit event" in {
      val userAnswers = emptyUserAnswers
        .set(ThirdPartyDataOwnerConsentPage, true)
        .success
        .value
        .set(EoriNumberPage, "GB123456789000")
        .success
        .value
        .set(ConfirmEoriPage, ConfirmEori.Yes)
        .success
        .value
        .set(ThirdPartyReferencePage, "ref")
        .success
        .value
        .set(ThirdPartyAccessStartDatePage, LocalDate.of(2025, 1, 1))
        .success
        .value
        .set(ThirdPartyAccessEndDatePage, Some(LocalDate.of(2025, 1, 1)))
        .success
        .value
        .set(DataTypesPage, Set(DataTypes.Export))
        .success
        .value
        .set(DeclarationDatePage, DeclarationDate.CustomDateRange)
        .success
        .value
        .set(DataStartDatePage, LocalDate.of(2025, 1, 1))
        .success
        .value
        .set(DataEndDatePage, Some(LocalDate.of(2025, 1, 1)))
        .success
        .value

      when(mockThirdPartyService.buildThirdPartyAddRequest(any(), any())).thenReturn(
        ThirdPartyRequest(
          userEORI = "GB987654321098",
          thirdPartyEORI = "GB123456123456",
          accessStart = Instant.parse("2025-09-09T00:00:00Z"),
          accessEnd = Some(Instant.parse("2025-09-09T10:59:38.334682780Z")),
          reportDateStart = Some(Instant.parse("2025-09-10T00:00:00Z")),
          reportDateEnd = Some(Instant.parse("2025-09-09T10:59:38.334716742Z")),
          accessType = Set("IMPORT", "EXPORT"),
          referenceName = Some("TestReport")
        )
      )

      when(mockTradeReportingExtractsService.createThirdPartyAddRequest(any())(any()))
        .thenReturn(Future.successful(ThirdPartyAddedConfirmation(thirdPartyEori = "GB123456123456")))

      when(mockTradeReportingExtractsService.getCompanyInformation(any())(any()))
        .thenReturn(Future.successful(CompanyInformation("businessInfo", ConsentStatus.Granted)))
      when(mockAuditService.auditThirdPartyAdded(any())(any())).thenReturn(Future.successful(()))

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          bind[TradeReportingExtractsService].toInstance(mockTradeReportingExtractsService),
          bind[ThirdPartyService].toInstance(mockThirdPartyService),
          bind[AuditService].toInstance(mockAuditService),
          bind[Clock].toInstance(fixedClock)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.AddThirdPartyCheckYourAnswersController.onSubmit().url)
        val result  = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual controllers.thirdparty.routes.ThirdPartyAddedConfirmationController
          .onPageLoad()
          .url
        verify(mockAuditService, times(1)).auditThirdPartyAdded(any())(any())
      }
    }

  }
}
