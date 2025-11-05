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
import controllers.report
import models.SectionNavigation
import models.report.Decision
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar.mock
import pages.report.DecisionPage
import play.api.inject
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import viewmodels.govuk.all.SummaryListViewModel
import views.html.report.CheckYourAnswersView

import scala.concurrent.Future

class CheckYourAnswersControllerSpec extends SpecBase {

  "CheckYourAnswers Controller" - {
    val sectionNav = SectionNavigation("reportRequestSection")

    "must return OK and the correct view for a GET" in {
      val userAnswers =
        emptyUserAnswers.set(sectionNav, "/request-customs-declaration-data/check-your-answers").success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, report.routes.CheckYourAnswersController.onPageLoad().url)

        val result = route(application, request).value

        val view = application.injector.instanceOf[CheckYourAnswersView]
        val list = SummaryListViewModel(Seq.empty)

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(list)(request, messages(application)).toString
      }
    }

    "must hide DecisionSummary when thirdPartyEori and dataTypes is only exports" in {
      val sectionNav     = SectionNavigation("reportRequestSection")
      val thirdPartyEori = "GB123456789000"
      val userAnswers    = emptyUserAnswers
        .set(sectionNav, "/request-customs-declaration-data/check-your-answers")
        .success
        .value
        .set(DecisionPage, Decision.Export)
        .success
        .value
        .set(pages.report.SelectThirdPartyEoriPage, thirdPartyEori)
        .success
        .value

      val mockTradeReportingExtractsService = mock[services.TradeReportingExtractsService]
      val thirdPartyDetails                 = models.ThirdPartyDetails(
        referenceName = Some("Test Name"),
        accessStartDate = java.time.LocalDate.now(),
        accessEndDate = None,
        dataTypes = Set("exports"),
        dataStartDate = None,
        dataEndDate = None
      )

      when(mockTradeReportingExtractsService.getAuthorisedBusinessDetails(any(), any())(any()))
        .thenReturn(Future.successful(thirdPartyDetails))

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(inject.bind[services.TradeReportingExtractsService].toInstance(mockTradeReportingExtractsService))
        .build()

      running(application) {
        val request = FakeRequest(GET, controllers.report.routes.CheckYourAnswersController.onPageLoad().url)
        val result  = route(application, request).value

        val content = contentAsString(result)
        content must not include "Type of data to download"
        status(result) mustEqual OK
      }
    }

    "must hide DecisionSummary when thirdPartyEori and dataTypes is only imports" in {
      val sectionNav     = SectionNavigation("reportRequestSection")
      val thirdPartyEori = "GB123456789000"
      val userAnswers    = emptyUserAnswers
        .set(sectionNav, "/request-customs-declaration-data/check-your-answers")
        .success
        .value
        .set(DecisionPage, Decision.Import)
        .success
        .value
        .set(pages.report.SelectThirdPartyEoriPage, thirdPartyEori)
        .success
        .value

      val mockTradeReportingExtractsService = mock[services.TradeReportingExtractsService]
      val thirdPartyDetails                 = models.ThirdPartyDetails(
        referenceName = Some("Test Name"),
        accessStartDate = java.time.LocalDate.now(),
        accessEndDate = None,
        dataTypes = Set("imports"),
        dataStartDate = None,
        dataEndDate = None
      )

      when(mockTradeReportingExtractsService.getAuthorisedBusinessDetails(any(), any())(any()))
        .thenReturn(Future.successful(thirdPartyDetails))

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(inject.bind[services.TradeReportingExtractsService].toInstance(mockTradeReportingExtractsService))
        .build()

      running(application) {
        val request = FakeRequest(GET, controllers.report.routes.CheckYourAnswersController.onPageLoad().url)
        val result  = route(application, request).value

        val content = contentAsString(result)
        content must not include "Type of data to download"
        status(result) mustEqual OK
      }
    }

    "must show DecisionSummary when thirdPartyEori and dataTypes is both imports and exports" in {
      val sectionNav     = SectionNavigation("reportRequestSection")
      val thirdPartyEori = "GB123456789000"
      val userAnswers    = emptyUserAnswers
        .set(sectionNav, "/request-customs-declaration-data/check-your-answers")
        .success
        .value
        .set(DecisionPage, Decision.Export)
        .success
        .value
        .set(pages.report.SelectThirdPartyEoriPage, thirdPartyEori)
        .success
        .value

      val mockTradeReportingExtractsService = mock[services.TradeReportingExtractsService]
      val thirdPartyDetails                 = models.ThirdPartyDetails(
        referenceName = Some("Test Name"),
        accessStartDate = java.time.LocalDate.now(),
        accessEndDate = None,
        dataTypes = Set("imports", "exports"),
        dataStartDate = None,
        dataEndDate = None
      )

      when(mockTradeReportingExtractsService.getAuthorisedBusinessDetails(any(), any())(any()))
        .thenReturn(Future.successful(thirdPartyDetails))

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(inject.bind[services.TradeReportingExtractsService].toInstance(mockTradeReportingExtractsService))
        .build()

      running(application) {
        val request = FakeRequest(GET, controllers.report.routes.CheckYourAnswersController.onPageLoad().url)
        val result  = route(application, request).value

        val content = contentAsString(result)
        content must include("Type of data to download")
        status(result) mustEqual OK
      }
    }
  }
}
