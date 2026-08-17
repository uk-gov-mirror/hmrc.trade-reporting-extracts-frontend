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
import exceptions.NoAuthorisedUserFoundException
import models.{CompanyInformation, ConsentStatus, ThirdPartyDetails}
import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.bind
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import services.TradeReportingExtractsService
import viewmodels.checkAnswers.thirdparty.{BusinessInfoSummary, DataTheyCanViewSummary, DataTypesSummary, EoriNumberSummary, ThirdPartyAccessPeriodSummary}
import viewmodels.govuk.all.SummaryListViewModel
import views.html.thirdparty.BusinessDetailsView

import java.time.LocalDate
import scala.concurrent.Future

class BusinessDetailsControllerSpec extends SpecBase with MockitoSugar {

  "BusinessDetailsController" - {

    val businessEori      = "GB123456789000"
    val companyInfo       = CompanyInformation("Test Business Ltd", ConsentStatus.Granted, false)
    val thirdPartyDetails = ThirdPartyDetails(
      referenceName = None,
      accessStartDate = LocalDate.of(2025, 1, 1),
      accessEndDate = Some(LocalDate.of(2025, 12, 31)),
      dataStartDate = Some(LocalDate.of(2025, 1, 1)),
      dataEndDate = Some(LocalDate.of(2025, 12, 31)),
      dataTypes = Set("Import")
    )

    "must return OK and render the correct view when company info is available and consent is granted" in {
      val mockTradeReportingExtractsService = mock[TradeReportingExtractsService]
      when(mockTradeReportingExtractsService.getCompanyInformation(any())(any()))
        .thenReturn(Future.successful(companyInfo))
      when(mockTradeReportingExtractsService.getAuthorisedBusinessDetails(any(), any())(any()))
        .thenReturn(Future.successful(thirdPartyDetails))

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
        .overrides(
          bind[TradeReportingExtractsService].toInstance(mockTradeReportingExtractsService)
        )
        .build()

      running(application) {
        val request = FakeRequest(GET, routes.BusinessDetailsController.onPageLoad(businessEori).url)
        val result  = route(application, request).value

        val view = application.injector.instanceOf[BusinessDetailsView]

        val list = SummaryListViewModel(
          Seq(
            EoriNumberSummary.detailsRow("GB123456789000")(messages(application)).get,
            BusinessInfoSummary.row("Test Business Ltd")(messages(application)).get,
            ThirdPartyAccessPeriodSummary.businessDetailsRow(thirdPartyDetails)(messages(application)).get,
            DataTypesSummary.businessDetailsRow(Set("Import"))(messages(application)).get,
            DataTheyCanViewSummary.businessDetailsRow(thirdPartyDetails)(messages(application)).get
          )
        )

        status(result) mustEqual OK
        contentAsString(result) mustEqual view.apply(list, "", true)(request, messages(application)).toString

        val document = Jsoup.parse(contentAsString(result))
        document.getElementsByClass("govuk-heading-xl").text() must include("Business details")
        document.text()                                        must include("Test Business Ltd")
        document.text()                                        must include("GB123456789000")
      }
    }

    "must return OK and render the view without business name when consent is denied" in {
      val deniedCompanyInfo = companyInfo.copy(consent = ConsentStatus.Denied)

      val mockTradeReportingExtractsService = mock[TradeReportingExtractsService]
      when(mockTradeReportingExtractsService.getCompanyInformation(any())(any()))
        .thenReturn(Future.successful(deniedCompanyInfo))
      when(mockTradeReportingExtractsService.getAuthorisedBusinessDetails(any(), any())(any()))
        .thenReturn(Future.successful(thirdPartyDetails))

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
        .overrides(
          bind[TradeReportingExtractsService].toInstance(mockTradeReportingExtractsService)
        )
        .build()

      running(application) {
        val request = FakeRequest(GET, routes.BusinessDetailsController.onPageLoad(businessEori).url)
        val result  = route(application, request).value

        val document = Jsoup.parse(contentAsString(result))
        document.text() must include("GB123456789000")
        document.text() must not include "Test Business Ltd"
      }
    }

    "must redirect to NoThirdPartyAccessController when NoAuthorisedUserFoundException is thrown by getAuthorisedBusinessDetails" in {
      val mockTradeReportingExtractsService = mock[TradeReportingExtractsService]
      when(mockTradeReportingExtractsService.getCompanyInformation(any())(any()))
        .thenReturn(Future.successful(companyInfo))
      when(mockTradeReportingExtractsService.getAuthorisedBusinessDetails(any(), any())(any()))
        .thenReturn(Future.failed(new NoAuthorisedUserFoundException("No access")))

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
        .overrides(
          bind[TradeReportingExtractsService].toInstance(mockTradeReportingExtractsService)
        )
        .build()

      running(application) {
        val request = FakeRequest(GET, routes.BusinessDetailsController.onPageLoad(businessEori).url)
        val result  = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result) mustBe Some(controllers.problem.routes.NoThirdPartyAccessController.onPageLoad().url)
      }
    }
  }
}
