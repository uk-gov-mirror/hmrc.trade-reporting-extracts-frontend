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

package controllers

import base.SpecBase
import config.FrontendAppConfig
import controllers.actions.{CustomFakeDataRetrievalOrCreateAction, DataRetrievalOrCreateAction, FakeIdentifierAction, IdentifierAction}
import models.thirdparty.DeclarationDate
import models.{CompanyInformation, ConsentStatus, ThirdPartyDetails, UserAnswers}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.mockito.MockitoSugar.mock
import pages.editThirdParty.{EditDataEndDatePage, EditDataStartDatePage, EditDeclarationDatePage, EditThirdPartyReferencePage}
import pages.report.NewEmailNotificationPage
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.RequestHeader
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import repositories.SessionRepository
import services.TradeReportingExtractsService
import viewmodels.checkAnswers.thirdparty.*
import viewmodels.govuk.all.SummaryListViewModel
import views.html.thirdparty.ThirdPartyDetailsView

import java.time.LocalDate
import scala.concurrent.Future

class ThirdPartyDetailsControllerSpec extends SpecBase with MockitoSugar {

  "ThirdPartyDetails Controller" - {

    val mockTradeReportingExtractsService: TradeReportingExtractsService = mock[TradeReportingExtractsService]
    val mockFrontendAppConfig: FrontendAppConfig                         = mock[FrontendAppConfig]
    val mockSessionRepository                                            = mock[SessionRepository]

    "must return OK and the correct view for a GET when consent given, no reference" in {

      val thirdPartyDetails = ThirdPartyDetails(
        referenceName = None,
        accessStartDate = LocalDate.of(2025, 1, 1),
        accessEndDate = None,
        dataTypes = Set("import"),
        dataStartDate = None,
        dataEndDate = None
      )

      when(mockTradeReportingExtractsService.getCompanyInformation(any())(any()))
        .thenReturn(Future.successful(CompanyInformation("foo", ConsentStatus.Granted, false)))

      when(mockTradeReportingExtractsService.getThirdPartyDetails(any(), any())(any()))
        .thenReturn(
          Future.successful(
            thirdPartyDetails
          )
        )

      when(mockFrontendAppConfig.feedbackUrl(any(classOf[RequestHeader]))).thenReturn("http://localhost/feedback")

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
        .overrides(
          bind[TradeReportingExtractsService].toInstance(mockTradeReportingExtractsService),
          bind[SessionRepository].toInstance(mockSessionRepository),
          bind[FrontendAppConfig].toInstance(mockFrontendAppConfig)
        )
        .build()

      running(application) {
        val request =
          FakeRequest(GET, controllers.thirdparty.routes.ThirdPartyDetailsController.onPageLoad("thirdPartyEori").url)

        val result = route(application, request).value

        val view = application.injector.instanceOf[ThirdPartyDetailsView]

        val list = SummaryListViewModel(
          Seq(
            EoriNumberSummary.detailsRow("thirdPartyEori")(messages(application)).get,
            BusinessInfoSummary.row("foo")(messages(application)).get,
            ThirdPartyAccessPeriodSummary
              .detailsRow(thirdPartyDetails, "thirdPartyEori", emptyUserAnswers)(messages(application))
              .get,
            DataTypesSummary.detailsRow(Set("import"), "thirdPartyEori")(messages(application)).get,
            DataTheyCanViewSummary
              .detailsRow(thirdPartyDetails, "thirdPartyEori", emptyUserAnswers)(messages(application))
              .get
          )
        )

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(list, "", true, false, "thirdPartyEori")(
          request,
          messages(application)
        ).toString
      }
    }

    "must return OK and the correct view for a GET when consent given with reference" in {

      val thirdPartyDetails = ThirdPartyDetails(
        referenceName = Some("bar"),
        accessStartDate = LocalDate.of(2025, 1, 1),
        accessEndDate = None,
        dataTypes = Set("import"),
        dataStartDate = None,
        dataEndDate = None
      )

      when(mockTradeReportingExtractsService.getCompanyInformation(any())(any()))
        .thenReturn(Future.successful(CompanyInformation("foo", ConsentStatus.Granted, false)))

      when(mockTradeReportingExtractsService.getThirdPartyDetails(any(), any())(any()))
        .thenReturn(
          Future.successful(
            thirdPartyDetails
          )
        )

      when(mockFrontendAppConfig.feedbackUrl(any(classOf[RequestHeader]))).thenReturn("http://localhost/feedback")

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
        .overrides(
          bind[TradeReportingExtractsService].toInstance(mockTradeReportingExtractsService),
          bind[SessionRepository].toInstance(mockSessionRepository),
          bind[FrontendAppConfig].toInstance(mockFrontendAppConfig)
        )
        .build()

      running(application) {
        val request =
          FakeRequest(GET, controllers.thirdparty.routes.ThirdPartyDetailsController.onPageLoad("thirdPartyEori").url)

        val result = route(application, request).value

        val view = application.injector.instanceOf[ThirdPartyDetailsView]

        val list = SummaryListViewModel(
          Seq(
            EoriNumberSummary.detailsRow("thirdPartyEori")(messages(application)).get,
            BusinessInfoSummary.row("foo")(messages(application)).get,
            ThirdPartyReferenceSummary.detailsRow(Some("bar"), "thirdPartyEori")(messages(application)).get,
            ThirdPartyAccessPeriodSummary
              .detailsRow(thirdPartyDetails, "thirdPartyEori", emptyUserAnswers)(messages(application))
              .get,
            DataTypesSummary.detailsRow(Set("import"), "thirdPartyEori")(messages(application)).get,
            DataTheyCanViewSummary
              .detailsRow(thirdPartyDetails, "thirdPartyEori", emptyUserAnswers)(messages(application))
              .get
          )
        )

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(list, "", true, false, "thirdPartyEori")(
          request,
          messages(application)
        ).toString
      }
    }

    "must return OK and the correct view for a GET when editThirdPartyEnabled is true" in {

      val thirdPartyDetails = ThirdPartyDetails(
        referenceName = Some("bar"),
        accessStartDate = LocalDate.of(2025, 1, 1),
        accessEndDate = None,
        dataTypes = Set("import"),
        dataStartDate = None,
        dataEndDate = None
      )

      when(mockTradeReportingExtractsService.getCompanyInformation(any())(any()))
        .thenReturn(Future.successful(CompanyInformation("foo", ConsentStatus.Granted, false)))

      when(mockTradeReportingExtractsService.getThirdPartyDetails(any(), any())(any()))
        .thenReturn(
          Future.successful(
            thirdPartyDetails
          )
        )

      when(mockFrontendAppConfig.feedbackUrl(any(classOf[RequestHeader]))).thenReturn("http://localhost/feedback")

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
        .overrides(
          bind[TradeReportingExtractsService].toInstance(mockTradeReportingExtractsService),
          bind[SessionRepository].toInstance(mockSessionRepository),
          bind[FrontendAppConfig].toInstance(mockFrontendAppConfig)
        )
        .build()

      running(application) {
        val request =
          FakeRequest(GET, controllers.thirdparty.routes.ThirdPartyDetailsController.onPageLoad("thirdPartyEori").url)

        val result = route(application, request).value

        val view = application.injector.instanceOf[ThirdPartyDetailsView]

        val list = SummaryListViewModel(
          Seq(
            EoriNumberSummary.detailsRow("thirdPartyEori")(messages(application)).get,
            BusinessInfoSummary.row("foo")(messages(application)).get,
            ThirdPartyReferenceSummary.detailsRow(Some("bar"), "thirdPartyEori")(messages(application)).get,
            ThirdPartyAccessPeriodSummary
              .detailsRow(thirdPartyDetails, "thirdPartyEori", emptyUserAnswers)(messages(application))
              .get,
            DataTypesSummary.detailsRow(Set("import"), "thirdPartyEori")(messages(application)).get,
            DataTheyCanViewSummary
              .detailsRow(thirdPartyDetails, "thirdPartyEori", emptyUserAnswers)(messages(application))
              .get
          )
        )

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(list, "", true, false, "thirdPartyEori")(
          request,
          messages(application)
        ).toString
      }
    }

    "must return OK and the correct view for a GET when consent not given with reference" in {

      val thirdPartyDetails = ThirdPartyDetails(
        referenceName = Some("bar"),
        accessStartDate = LocalDate.of(2025, 1, 1),
        accessEndDate = None,
        dataTypes = Set("import"),
        dataStartDate = None,
        dataEndDate = None
      )

      when(mockTradeReportingExtractsService.getCompanyInformation(any())(any()))
        .thenReturn(Future.successful(CompanyInformation("foo", ConsentStatus.Denied, false)))

      when(mockTradeReportingExtractsService.getThirdPartyDetails(any(), any())(any()))
        .thenReturn(
          Future.successful(
            thirdPartyDetails
          )
        )

      when(mockFrontendAppConfig.feedbackUrl(any(classOf[RequestHeader]))).thenReturn("http://localhost/feedback")

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
        .overrides(
          bind[TradeReportingExtractsService].toInstance(mockTradeReportingExtractsService),
          bind[SessionRepository].toInstance(mockSessionRepository),
          bind[FrontendAppConfig].toInstance(mockFrontendAppConfig)
        )
        .build()

      running(application) {
        val request =
          FakeRequest(GET, controllers.thirdparty.routes.ThirdPartyDetailsController.onPageLoad("thirdPartyEori").url)

        val result = route(application, request).value

        val view = application.injector.instanceOf[ThirdPartyDetailsView]

        val list = SummaryListViewModel(
          Seq(
            EoriNumberSummary.detailsRow("thirdPartyEori")(messages(application)).get,
            ThirdPartyReferenceSummary.detailsRow(Some("bar"), "thirdPartyEori")(messages(application)).get,
            ThirdPartyAccessPeriodSummary
              .detailsRow(thirdPartyDetails, "thirdPartyEori", emptyUserAnswers)(messages(application))
              .get,
            DataTypesSummary.detailsRow(Set("import"), "thirdPartyEori")(messages(application)).get,
            DataTheyCanViewSummary
              .detailsRow(thirdPartyDetails, "thirdPartyEori", emptyUserAnswers)(messages(application))
              .get
          )
        )

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(list, "", true, false, "eori")(request, messages(application)).toString
      }
    }

    "must return OK and the correct view for a GET when consent not given with no reference" in {

      val thirdPartyDetails = ThirdPartyDetails(
        referenceName = None,
        accessStartDate = LocalDate.of(2025, 1, 1),
        accessEndDate = None,
        dataTypes = Set("import"),
        dataStartDate = None,
        dataEndDate = None
      )

      when(mockTradeReportingExtractsService.getCompanyInformation(any())(any()))
        .thenReturn(Future.successful(CompanyInformation("foo", ConsentStatus.Denied, false)))

      when(mockTradeReportingExtractsService.getThirdPartyDetails(any(), any())(any()))
        .thenReturn(
          Future.successful(
            thirdPartyDetails
          )
        )

      when(mockFrontendAppConfig.feedbackUrl(any(classOf[RequestHeader]))).thenReturn("http://localhost/feedback")

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
        .overrides(
          bind[TradeReportingExtractsService].toInstance(mockTradeReportingExtractsService),
          bind[SessionRepository].toInstance(mockSessionRepository),
          bind[FrontendAppConfig].toInstance(mockFrontendAppConfig)
        )
        .build()

      running(application) {
        val request =
          FakeRequest(GET, controllers.thirdparty.routes.ThirdPartyDetailsController.onPageLoad("thirdPartyEori").url)

        val result = route(application, request).value

        val view = application.injector.instanceOf[ThirdPartyDetailsView]

        val list = SummaryListViewModel(
          Seq(
            EoriNumberSummary.detailsRow("thirdPartyEori")(messages(application)).get,
            ThirdPartyReferenceSummary.detailsRow(None, "thirdPartyEori")(messages(application)).get,
            ThirdPartyAccessPeriodSummary
              .detailsRow(thirdPartyDetails, "thirdPartyEori", emptyUserAnswers)(messages(application))
              .get,
            DataTypesSummary.detailsRow(Set("import"), "thirdPartyEori")(messages(application)).get,
            DataTheyCanViewSummary
              .detailsRow(thirdPartyDetails, "thirdPartyEori", emptyUserAnswers)(messages(application))
              .get
          )
        )

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(list, "", true, false, "thirdPartyEori")(
          request,
          messages(application)
        ).toString
      }
    }

    "must display confirm changes and cancel buttons when there are changes" in {

      val thirdPartyDetails = ThirdPartyDetails(
        referenceName = Some("bar"),
        accessStartDate = LocalDate.of(2025, 1, 1),
        accessEndDate = None,
        dataTypes = Set("import"),
        dataStartDate = None,
        dataEndDate = None
      )

      when(mockTradeReportingExtractsService.getCompanyInformation(any())(any()))
        .thenReturn(Future.successful(CompanyInformation("foo", ConsentStatus.Denied, false)))

      when(mockTradeReportingExtractsService.getThirdPartyDetails(any(), any())(any()))
        .thenReturn(Future.successful(thirdPartyDetails))

      when(mockFrontendAppConfig.feedbackUrl(any(classOf[RequestHeader]))).thenReturn("http://localhost/feedback")

      val modifiedUserAnswers = UserAnswers("id")
        .set(pages.editThirdParty.EditThirdPartyReferencePage("thirdPartyEori"), "changedRef")
        .success
        .value

      val application = new GuiceApplicationBuilder()
        .overrides(
          bind[DataRetrievalOrCreateAction].toInstance(new CustomFakeDataRetrievalOrCreateAction(modifiedUserAnswers)),
          bind[IdentifierAction].to[FakeIdentifierAction],
          bind[TradeReportingExtractsService].toInstance(mockTradeReportingExtractsService),
          bind[SessionRepository].toInstance(mockSessionRepository),
          bind[FrontendAppConfig].toInstance(mockFrontendAppConfig)
        )
        .build()

      running(application) {
        val request =
          FakeRequest(GET, controllers.thirdparty.routes.ThirdPartyDetailsController.onPageLoad("thirdPartyEori").url)
        val result  = route(application, request).value

        val content = contentAsString(result)

        status(result) mustEqual OK
        content must include(messages(application)("editThirdParty.confirmChanges"))
        content must include(messages(application)("editThirdParty.cancel"))
      }
    }

    "must display confirm changes when EditDataStartDatePage answer differs from original" in {

      val thirdPartyDetails = ThirdPartyDetails(
        referenceName = Some("bar"),
        accessStartDate = LocalDate.of(2025, 1, 1),
        accessEndDate = None,
        dataTypes = Set("import"),
        dataStartDate = Some(LocalDate.of(2025, 1, 1)),
        dataEndDate = None
      )

      when(mockTradeReportingExtractsService.getCompanyInformation(any())(any()))
        .thenReturn(Future.successful(CompanyInformation("foo", ConsentStatus.Denied, false)))

      when(mockTradeReportingExtractsService.getThirdPartyDetails(any(), any())(any()))
        .thenReturn(Future.successful(thirdPartyDetails))

      when(mockFrontendAppConfig.feedbackUrl(any(classOf[RequestHeader]))).thenReturn("http://localhost/feedback")

      val modifiedUserAnswers = UserAnswers("id")
        .set(EditDataStartDatePage("thirdPartyEori"), LocalDate.of(2025, 2, 1))
        .success
        .value

      val application = new GuiceApplicationBuilder()
        .overrides(
          bind[DataRetrievalOrCreateAction].toInstance(new CustomFakeDataRetrievalOrCreateAction(modifiedUserAnswers)),
          bind[IdentifierAction].to[FakeIdentifierAction],
          bind[TradeReportingExtractsService].toInstance(mockTradeReportingExtractsService),
          bind[SessionRepository].toInstance(mockSessionRepository),
          bind[FrontendAppConfig].toInstance(mockFrontendAppConfig)
        )
        .build()

      running(application) {
        val request =
          FakeRequest(GET, controllers.thirdparty.routes.ThirdPartyDetailsController.onPageLoad("thirdPartyEori").url)
        val result  = route(application, request).value
        val content = contentAsString(result)

        status(result) mustEqual OK
        content must include(messages(application)("editThirdParty.confirmChanges"))
      }
    }

    "must display confirm changes when data start absent in answers but EditDeclarationDatePage is AllAvailableData and original present" in {

      val thirdPartyDetails = ThirdPartyDetails(
        referenceName = Some("bar"),
        accessStartDate = LocalDate.of(2025, 1, 1),
        accessEndDate = None,
        dataTypes = Set("import"),
        dataStartDate = Some(LocalDate.of(2025, 1, 1)),
        dataEndDate = None
      )

      when(mockTradeReportingExtractsService.getCompanyInformation(any())(any()))
        .thenReturn(Future.successful(CompanyInformation("foo", ConsentStatus.Denied, false)))

      when(mockTradeReportingExtractsService.getThirdPartyDetails(any(), any())(any()))
        .thenReturn(Future.successful(thirdPartyDetails))

      when(mockFrontendAppConfig.feedbackUrl(any(classOf[RequestHeader]))).thenReturn("http://localhost/feedback")

      val modifiedUserAnswers = UserAnswers("id")
        .set(EditDeclarationDatePage("thirdPartyEori"), DeclarationDate.AllAvailableData)
        .success
        .value

      val application = new GuiceApplicationBuilder()
        .overrides(
          bind[DataRetrievalOrCreateAction].toInstance(new CustomFakeDataRetrievalOrCreateAction(modifiedUserAnswers)),
          bind[IdentifierAction].to[FakeIdentifierAction],
          bind[TradeReportingExtractsService].toInstance(mockTradeReportingExtractsService),
          bind[SessionRepository].toInstance(mockSessionRepository),
          bind[FrontendAppConfig].toInstance(mockFrontendAppConfig)
        )
        .build()

      running(application) {
        val request =
          FakeRequest(GET, controllers.thirdparty.routes.ThirdPartyDetailsController.onPageLoad("thirdPartyEori").url)
        val result  = route(application, request).value
        val content = contentAsString(result)

        status(result) mustEqual OK
        content must include(messages(application)("editThirdParty.confirmChanges"))
      }
    }

    "must display confirm changes when EditDataEndDatePage answer is set and differs from original" in {

      val thirdPartyDetails = ThirdPartyDetails(
        referenceName = Some("bar"),
        accessStartDate = LocalDate.of(2025, 1, 1),
        accessEndDate = None,
        dataTypes = Set("import"),
        dataStartDate = None,
        dataEndDate = None
      )

      when(mockTradeReportingExtractsService.getCompanyInformation(any())(any()))
        .thenReturn(Future.successful(CompanyInformation("foo", ConsentStatus.Denied, false)))

      when(mockTradeReportingExtractsService.getThirdPartyDetails(any(), any())(any()))
        .thenReturn(Future.successful(thirdPartyDetails))

      when(mockFrontendAppConfig.feedbackUrl(any(classOf[RequestHeader]))).thenReturn("http://localhost/feedback")

      val modifiedUserAnswers = UserAnswers("id")
        .set(EditDataEndDatePage("thirdPartyEori"), Some(LocalDate.of(2025, 3, 1)))
        .success
        .value

      val application = new GuiceApplicationBuilder()
        .overrides(
          bind[DataRetrievalOrCreateAction].toInstance(new CustomFakeDataRetrievalOrCreateAction(modifiedUserAnswers)),
          bind[IdentifierAction].to[FakeIdentifierAction],
          bind[TradeReportingExtractsService].toInstance(mockTradeReportingExtractsService),
          bind[SessionRepository].toInstance(mockSessionRepository),
          bind[FrontendAppConfig].toInstance(mockFrontendAppConfig)
        )
        .build()

      running(application) {
        val request =
          FakeRequest(GET, controllers.thirdparty.routes.ThirdPartyDetailsController.onPageLoad("thirdPartyEori").url)
        val result  = route(application, request).value
        val content = contentAsString(result)

        status(result) mustEqual OK
        content must include(messages(application)("editThirdParty.confirmChanges"))
      }
    }

    "must display confirm changes when data end absent in answers but EditDeclarationDatePage is AllAvailableData and original present" in {

      val thirdPartyDetails = ThirdPartyDetails(
        referenceName = Some("bar"),
        accessStartDate = LocalDate.of(2025, 1, 1),
        accessEndDate = Some(LocalDate.of(2025, 4, 1)),
        dataTypes = Set("import"),
        dataStartDate = None,
        dataEndDate = None
      )

      when(mockTradeReportingExtractsService.getCompanyInformation(any())(any()))
        .thenReturn(Future.successful(CompanyInformation("foo", ConsentStatus.Denied, false)))

      when(mockTradeReportingExtractsService.getThirdPartyDetails(any(), any())(any()))
        .thenReturn(Future.successful(thirdPartyDetails))

      when(mockFrontendAppConfig.feedbackUrl(any(classOf[RequestHeader]))).thenReturn("http://localhost/feedback")

      val modifiedUserAnswers = UserAnswers("id")
        .set(EditDeclarationDatePage("thirdPartyEori"), DeclarationDate.AllAvailableData)
        .success
        .value

      val application = new GuiceApplicationBuilder()
        .overrides(
          bind[DataRetrievalOrCreateAction].toInstance(new CustomFakeDataRetrievalOrCreateAction(modifiedUserAnswers)),
          bind[IdentifierAction].to[FakeIdentifierAction],
          bind[TradeReportingExtractsService].toInstance(mockTradeReportingExtractsService),
          bind[SessionRepository].toInstance(mockSessionRepository),
          bind[FrontendAppConfig].toInstance(mockFrontendAppConfig)
        )
        .build()

      running(application) {
        val request =
          FakeRequest(GET, controllers.thirdparty.routes.ThirdPartyDetailsController.onPageLoad("thirdPartyEori").url)
        val result  = route(application, request).value
        val content = contentAsString(result)

        status(result) mustEqual OK
        content must include(messages(application)("editThirdParty.confirmChanges"))
      }
    }

    "removeAnswersAndRedirect" - {

      "must redirect the user and delete userAnswers" in {
        val userAnswersCaptor = ArgumentCaptor.forClass(classOf[UserAnswers])

        when(mockSessionRepository.set(userAnswersCaptor.capture())).thenReturn(Future.successful(true))

        when(mockTradeReportingExtractsService.getCompanyInformation(any())(any()))
          .thenReturn(Future.successful(CompanyInformation("foo", ConsentStatus.Granted, false)))

        when(mockTradeReportingExtractsService.getThirdPartyDetails(any(), any())(any()))
          .thenReturn(
            Future.successful(
              ThirdPartyDetails(
                referenceName = Some("bar"),
                accessStartDate = LocalDate.of(2025, 1, 1),
                accessEndDate = None,
                dataTypes = Set("import"),
                dataStartDate = None,
                dataEndDate = None
              )
            )
          )

        val ua = emptyUserAnswers
          .set(EditThirdPartyReferencePage("thirdParty1"), "refTP1")
          .success
          .value
          .set(EditThirdPartyReferencePage("thirdParty2"), "refTP2")
          .success
          .value
          .set(NewEmailNotificationPage, "someEori")
          .success
          .value

        val application = applicationBuilder(userAnswers = Some(ua))
          .overrides(
            bind[TradeReportingExtractsService].toInstance(mockTradeReportingExtractsService),
            bind[SessionRepository].toInstance(mockSessionRepository)
          )
          .build()

        running(application) {
          val request = FakeRequest(
            GET,
            controllers.thirdparty.routes.ThirdPartyDetailsController.removeAnswersAndRedirect("thirdParty1").url
          )

          val result = route(application, request).value

          status(result) mustEqual SEE_OTHER
          val capturedAnswers = userAnswersCaptor.getValue
          capturedAnswers.get(EditThirdPartyReferencePage("thirdParty1")) mustBe None
          capturedAnswers.get(EditThirdPartyReferencePage("thirdParty2")) mustBe Some("refTP2")
          redirectLocation(result).value mustEqual controllers.thirdparty.routes.AuthorisedThirdPartiesController
            .onPageLoad()
            .url
        }
      }
    }

  }
}
