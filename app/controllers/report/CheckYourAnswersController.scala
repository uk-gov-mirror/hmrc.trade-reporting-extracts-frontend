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

import com.google.inject.Inject
import config.FrontendAppConfig
import controllers.BaseController
import controllers.actions.*
import models.report.ReportTypeImport
import navigation.ReportNavigator
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.TradeReportingExtractsService
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.SummaryListRow
import viewmodels.checkAnswers.report.*
import viewmodels.govuk.summarylist.*
import views.html.report.CheckYourAnswersView

import scala.concurrent.{ExecutionContext, Future}

class CheckYourAnswersController @Inject() (appConfig: FrontendAppConfig)(
  override val messagesApi: MessagesApi,
  identify: IdentifierAction,
  getData: DataRetrievalAction,
  requireData: DataRequiredAction,
  navigator: ReportNavigator,
  preventBackNavigationAfterSubmissionAction: PreventBackNavigationAfterSubmissionAction,
  missingDependentAnswersAction: MissingDependentAnswersAction,
  val controllerComponents: MessagesControllerComponents,
  tradeReportingExtractsService: TradeReportingExtractsService,
  view: CheckYourAnswersView
)(implicit ec: ExecutionContext)
    extends BaseController {

  def onPageLoad(): Action[AnyContent] = (identify
    andThen getData
    andThen requireData
    andThen preventBackNavigationAfterSubmissionAction
    andThen missingDependentAnswersAction).async { implicit request =>

    val reportTypeImports: Set[ReportTypeImport] =
      request.userAnswers.get(pages.report.ReportTypeImportPage).getOrElse(Set.empty)

    val thirdPartyEoriOpt = request.userAnswers.get(pages.report.SelectThirdPartyEoriPage)

    thirdPartyEoriOpt match {
      case Some(thirdPartyEori) =>
        tradeReportingExtractsService.getAuthorisedBusinessDetails(request.eori, thirdPartyEori).map {
          thirdPartyDetails =>
            val dataTypes           = thirdPartyDetails.dataTypes
            val showDecisionSummary = !(dataTypes == Set("exports") || dataTypes == Set("imports"))

            val rows: Seq[Option[SummaryListRow]] = Seq(
              if (appConfig.thirdPartyEnabled) ChooseEoriSummary.row(request.userAnswers, request.eori) else None,
              if (showDecisionSummary) DecisionSummary.row(request.userAnswers) else None,
              EoriRoleSummary.row(request.userAnswers),
              ReportTypeImportSummary.row(request.userAnswers),
              ReportDateRangeSummary.row(request.userAnswers),
              ReportNameSummary.row(request.userAnswers),
              MaybeAdditionalEmailSummary.row(request.userAnswers),
              EmailSelectionSummary.row(request.userAnswers)
            )

            val list = SummaryListViewModel(rows = rows.flatten)
            Ok(view(list))
        }
      case None                 =>
        Future.successful {
          val rows: Seq[Option[SummaryListRow]] = Seq(
            if (appConfig.thirdPartyEnabled) ChooseEoriSummary.row(request.userAnswers, request.eori) else None,
            DecisionSummary.row(request.userAnswers),
            EoriRoleSummary.row(request.userAnswers),
            ReportTypeImportSummary.row(request.userAnswers),
            ReportDateRangeSummary.row(request.userAnswers),
            ReportNameSummary.row(request.userAnswers),
            MaybeAdditionalEmailSummary.row(request.userAnswers),
            EmailSelectionSummary.row(request.userAnswers)
          )
          val list                              = SummaryListViewModel(rows = rows.flatten)
          Ok(view(list))
        }
    }
  }
}
