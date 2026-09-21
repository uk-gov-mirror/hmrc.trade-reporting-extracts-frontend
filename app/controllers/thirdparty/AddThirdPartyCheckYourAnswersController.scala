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
import play.api.Logging
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repositories.SessionRepository
import services.{AuditService, ThirdPartyService, TradeReportingExtractsService}
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.SummaryListRow
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.DateTimeFormats.dateTimeFormat
import utils.ThirdPartyFieldsValidator
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
    with I18nSupport
    with Logging {

  def onPageLoad: Action[AnyContent] = (identify
    andThen getData
    andThen requireData
    andThen preventBackNavigationAfterAddThirdPartyAction).async { implicit request =>

    val userAnswers      = request.userAnswers
    val validationResult = ThirdPartyFieldsValidator.validateMandatoryFields(userAnswers)
    request.userAnswers.get(EoriNumberPage) match {
      case Some(eoriNumber) =>
        if (!validationResult) {
          redirectOnProblem(userAnswers).flatMap(_ =>
            Future.successful(Redirect(controllers.problem.routes.ThirdPartyIssueController.onPageLoad()))
          )
        } else {
          for {
            companyInfo     <- tradeReportingExtractsService.getCompanyInformation(eoriNumber)
            maybeCompanyName = resolveDisplayName(companyInfo)
            rows             = rowGenerator(userAnswers, maybeCompanyName)
            list             = SummaryListViewModel(rows = rows.flatten)
          } yield Ok(view(list))
        }
      case None             =>
        redirectOnProblem(userAnswers).flatMap(_ =>
          Future.successful(Redirect(controllers.problem.routes.AddThirdPartyGeneralProblemController.onPageLoad()))
        )
    }

  }

  def onSubmit(): Action[AnyContent] = (identify
    andThen getData
    andThen requireData
    andThen preventBackNavigationAfterAddThirdPartyAction).async { implicit request =>
    val userAnswers      = request.userAnswers
    val validationResult = ThirdPartyFieldsValidator.validateMandatoryFields(userAnswers)
    request.userAnswers.get(EoriNumberPage) match {
      case Some(eoriNumber) =>
        if (!validationResult) {
          redirectOnProblem(userAnswers).flatMap(_ =>
            Future.successful(Redirect(controllers.problem.routes.ThirdPartyIssueController.onPageLoad()))
          )
        } else {
          for {
            thirdPartyAddedConfirmation <-
              tradeReportingExtractsService.createThirdPartyAddRequest(
                thirdPartyService.buildThirdPartyAddRequest(request.userAnswers, request.eori)
              )
            companyInfo                 <- tradeReportingExtractsService.getCompanyInformation(eoriNumber)
            maybeCompanyName             = resolveDisplayName(companyInfo)
            _                           <- buildThirdPartyAddedAuditEvent(request, maybeCompanyName) match {
                                             case Some(event) => auditService.auditThirdPartyAdded(event)
                                             case None        => Future.successful(logger.warn(s"Could not build audit event for third party addition"))
                                           }
            updatedAnswers               = AddThirdPartySection.removeAllAddThirdPartyAnswersAndNavigation(request.userAnswers)
            updatedAnswersWithFlag      <- Future.fromTry(updatedAnswers.set(AlreadyAddedThirdPartyFlag(), true))

            submittedDate  = LocalDate.now(clock).format(dateTimeFormat()(messagesApi.preferred(request).lang))
            submissionMeta = ThirdPartySubmissionMeta(
                               thirdPartyEori = thirdPartyAddedConfirmation.thirdPartyEori,
                               companyName = maybeCompanyName,
                               submittedDate = LocalDate.now(clock)
                             )

            userAnswersWithMeta =
              updatedAnswersWithFlag.copy(submissionMeta = Some(Json.toJson(submissionMeta).as[JsObject]))
            _                  <- sessionRepository.set(userAnswersWithMeta)

          } yield Redirect(navigator.nextPage(AddThirdPartyCheckYourAnswersPage, userAnswers = request.userAnswers))
        }
      case None             =>
        redirectOnProblem(userAnswers).flatMap(_ =>
          Future.successful(Redirect(controllers.problem.routes.AddThirdPartyGeneralProblemController.onPageLoad()))
        )
    }
  }

  private def redirectOnProblem(userAnswers: UserAnswers): Future[Boolean] = {
    val updatedAnswers = AddThirdPartySection
      .removeAllAddThirdPartyAnswersAndNavigation(userAnswers)
      .set(AlreadyAddedThirdPartyFlag(), true)
      .get
    sessionRepository.set(updatedAnswers)
  }

  private def rowGenerator(answers: UserAnswers, maybeBusinessInfo: Option[String])(implicit
    messages: Messages
  ): Seq[Option[SummaryListRow]] =
    Seq(
      ThirdPartyDataOwnerConsentSummary.row(answers),
      EoriNumberSummary.checkYourAnswersRow(answers),
      maybeBusinessInfo match {
        case Some(businessInfo) => BusinessInfoSummary.row(businessInfo)
        case None               => ThirdPartyReferenceSummary.checkYourAnswersRow(answers)
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
  ): Option[ThirdPartyAddedEvent] = {

    val userAnswers = request.userAnswers

    for {
      isImporterExporterForDataToShare <- userAnswers.get(ThirdPartyDataOwnerConsentPage)
      confirmEori                      <- userAnswers.get(ConfirmEoriPage)
      declarationDate                  <- userAnswers.get(DeclarationDatePage)
      thirdPartyEori                   <- userAnswers.get(EoriNumberPage)
      thirdPartyAccessStart            <- userAnswers.get(ThirdPartyAccessStartDatePage)
      dataTypes                        <- userAnswers.get(DataTypesPage)
    } yield {
      val thirdPartyEoriAccessGiven = confirmEori match {
        case ConfirmEori.Yes => true
        case ConfirmEori.No  => false
      }

      val thirdPartyGivenAccessAllData = declarationDate match {
        case DeclarationDate.AllAvailableData => true
        case DeclarationDate.CustomDateRange  => false
      }

      val thirdPartyAccessStartStr =
        thirdPartyAccessStart.atStartOfDay().toInstant(ZoneOffset.UTC).toString

      val thirdPartyAccessEndStr = userAnswers.get(ThirdPartyAccessEndDatePage) match {
        case Some(Some(endDate)) => endDate.atStartOfDay().toInstant(ZoneOffset.UTC).toString
        case _                   => "indefinite"
      }

      val dataAccessTypeStr = dataTypes match {
        case set if set.contains(DataTypes.Export) && set.contains(DataTypes.Import) => "import, export"
        case set if set.contains(DataTypes.Export)                                   => "export"
        case _                                                                       => "import"
      }

      val thirdPartyDataStartStr = userAnswers.get(DataStartDatePage) match {
        case None            => "all available data"
        case Some(startDate) => startDate.atStartOfDay().toInstant(ZoneOffset.UTC).toString
      }

      val thirdPartyDataEndStr = userAnswers.get(DataEndDatePage) match {
        case Some(Some(endDate)) => endDate.atStartOfDay().toInstant(ZoneOffset.UTC).toString
        case _                   => "all available data"
      }

      ThirdPartyAddedEvent(
        IsImporterExporterForDataToShare = isImporterExporterForDataToShare,
        thirdPartyEoriAccessGiven = thirdPartyEoriAccessGiven,
        thirdPartyGivenAccessAllData = thirdPartyGivenAccessAllData,
        requesterEori = request.eori,
        thirdPartyEori = thirdPartyEori,
        thirdPartyBusinessInformation = maybeCompanyName,
        thirdPartyReferenceName = userAnswers.get(ThirdPartyReferencePage),
        thirdPartyAccessStart = thirdPartyAccessStartStr,
        thirdPartyAccessEnd = thirdPartyAccessEndStr,
        dataAccessType = dataAccessTypeStr,
        thirdPartyDataStart = thirdPartyDataStartStr,
        thirdPartyDataEnd = thirdPartyDataEndStr
      )
    }
  }

}
