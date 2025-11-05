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

import controllers.actions.*
import models.requests.DataRequest
import models.thirdparty.*
import models.{AlreadyAddedThirdPartyFlag, CompanyInformation, ConsentStatus, UserAnswers}
import navigation.ThirdPartyNavigator
import pages.thirdparty.*
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repositories.SessionRepository
import services.{AuditService, ThirdPartyService, TradeReportingExtractsService}
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.SummaryListRow
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.DateTimeFormats.dateTimeFormat
import utils.json.OptionalLocalDateReads.*
import viewmodels.checkAnswers.thirdparty.*
import viewmodels.govuk.all.SummaryListViewModel
import views.html.thirdparty.AddThirdPartyCheckYourAnswersView

import java.time.{Clock, LocalDate, ZoneOffset}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class AddThirdPartyCheckYourAnswersController @Inject() (
  override val messagesApi: MessagesApi,
  identify: IdentifierAction,
  getData: DataRetrievalAction,
  navigator: ThirdPartyNavigator,
  preventBackNavigationAfterAddThirdPartyAction: PreventBackNavigationAfterAddThirdPartyAction,
  requireData: DataRequiredAction,
  val controllerComponents: MessagesControllerComponents,
  view: AddThirdPartyCheckYourAnswersView,
  tradeReportingExtractsService: TradeReportingExtractsService,
  thirdPartyService: ThirdPartyService,
  sessionRepository: SessionRepository,
  auditService: AuditService,
  clock: Clock
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  def onPageLoad: Action[AnyContent] = (identify
    andThen getData
    andThen requireData
    andThen preventBackNavigationAfterAddThirdPartyAction).async { implicit request =>

    val userAnsewrs = request.userAnswers

    for {
      companyInfo     <- tradeReportingExtractsService.getCompanyInformation(userAnsewrs.get(EoriNumberPage).get)
      maybeCompanyName = resolveDisplayName(companyInfo)
      rows             = rowGenerator(userAnsewrs, maybeCompanyName)
      list             = SummaryListViewModel(rows = rows.flatten)
    } yield Ok(view(list))
  }

  def onSubmit(): Action[AnyContent] = (identify
    andThen getData
    andThen requireData
    andThen preventBackNavigationAfterAddThirdPartyAction).async { implicit request =>
    for {
      thirdPartyAddedConfirmation <- tradeReportingExtractsService.createThirdPartyAddRequest(
                                       thirdPartyService.buildThirdPartyAddRequest(request.userAnswers, request.eori)
                                     )

      companyInfo            <- tradeReportingExtractsService.getCompanyInformation(request.userAnswers.get(EoriNumberPage).get)
      maybeCompanyName        = resolveDisplayName(companyInfo)
      _                      <- auditService.auditThirdPartyAdded(buildThirdPartyAddedAuditEvent(request, maybeCompanyName))
      updatedAnswers          = AddThirdPartySection.removeAllAddThirdPartyAnswersAndNavigation(request.userAnswers)
      updatedAnswersWithFlag <- Future.fromTry(updatedAnswers.set(AlreadyAddedThirdPartyFlag(), true))

      submittedDate  = LocalDate.now(clock).format(dateTimeFormat()(messagesApi.preferred(request).lang))
      submissionMeta = ThirdPartySubmissionMeta(
                         thirdPartyEori = thirdPartyAddedConfirmation.thirdPartyEori,
                         companyName = maybeCompanyName,
                         submittedDate = submittedDate
                       )

      userAnswersWithMeta = updatedAnswersWithFlag.copy(submissionMeta = Some(Json.toJson(submissionMeta).as[JsObject]))
      _                  <- sessionRepository.set(userAnswersWithMeta)

    } yield Redirect(navigator.nextPage(AddThirdPartyCheckYourAnswersPage, userAnswers = request.userAnswers))
  }

  private def rowGenerator(answers: UserAnswers, maybeBusinessInfo: Option[String])(implicit
    messages: Messages
  ): Seq[Option[SummaryListRow]] =
    Seq(
      ThirdPartyDataOwnerConsentSummary.row(answers),
      EoriNumberSummary.checkYourAnswersRow(answers),
      if (maybeBusinessInfo.isDefined) {
        BusinessInfoSummary.row(maybeBusinessInfo.get)
      } else {
        ThirdPartyReferenceSummary.checkYourAnswersRow(answers)
      },
      ThirdPartyAccessPeriodSummary.checkYourAnswersRow(answers),
      DataTypesSummary.checkYourAnswersRow(answers),
      DeclarationDateSummary.row(answers),
      DataTheyCanViewSummary.checkYourAnswersRow(answers)
    )

  private def resolveDisplayName(companyInfo: CompanyInformation): Option[String] =
    companyInfo.consent match {
      case ConsentStatus.Denied => None
      case _                    => Some(companyInfo.name)
    }

  private def buildThirdPartyAddedAuditEvent(
    request: DataRequest[AnyContent],
    maybeCompanyName: Option[String]
  ): ThirdPartyAddedEvent = {
    val userAnswers = request.userAnswers

    ThirdPartyAddedEvent(
      IsImporterExporterForDataToShare = userAnswers.get(ThirdPartyDataOwnerConsentPage).get,
      thirdPartyEoriAccessGiven = userAnswers.get(ConfirmEoriPage).get match {
        case ConfirmEori.Yes => true
        case ConfirmEori.No  => false
      },
      thirdPartyGivenAccessAllData = userAnswers.get(DeclarationDatePage).get match {
        case DeclarationDate.AllAvailableData => true
        case DeclarationDate.CustomDateRange  => false
      },
      requesterEori = request.eori,
      thirdPartyEori = userAnswers.get(EoriNumberPage).get,
      thirdPartyBusinessInformation = maybeCompanyName,
      thirdPartyReferenceName = userAnswers.get(ThirdPartyReferencePage),
      thirdPartyAccessStart =
        userAnswers.get(ThirdPartyAccessStartDatePage).get.atStartOfDay().toInstant(ZoneOffset.UTC).toString,
      thirdPartyAccessEnd = userAnswers.get(ThirdPartyAccessEndDatePage) match {
        case Some(Some(endDate)) => endDate.atStartOfDay().toInstant(ZoneOffset.UTC).toString
        case _                   => "indefinite"
      },
      dataAccessType = userAnswers.get(DataTypesPage).get match {
        case set if set.contains(DataTypes.Export) && set.contains(DataTypes.Import) => "import, export"
        case set if set.contains(DataTypes.Export)                                   => "export"
        case _                                                                       => "import"
      },
      thirdPartyDataStart = userAnswers.get(DataStartDatePage) match {
        case None            => "all available data"
        case Some(startDate) => startDate.atStartOfDay().toInstant(ZoneOffset.UTC).toString
      },
      thirdPartyDataEnd = userAnswers.get(DataEndDatePage) match {
        case Some(Some(endDate)) => endDate.atStartOfDay().toInstant(ZoneOffset.UTC).toString
        case _                   => "all available data"
      }
    )
  }
}
