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

package controllers.editThirdParty

import com.google.inject.Inject
import controllers.BaseController
import controllers.actions.{DataRequiredAction, DataRetrievalAction, IdentifierAction}
import models.ThirdPartyDetails
import models.requests.DataRequest
import models.thirdparty.{DataUpdate, DeclarationDate, ThirdPartyRequest, ThirdPartyUpdatedEvent}
import pages.editThirdParty.{EditDataEndDatePage, EditDataStartDatePage, EditDeclarationDatePage, EditThirdPartyAccessEndDatePage, EditThirdPartyAccessStartDatePage, EditThirdPartyDataTypesPage, EditThirdPartyReferencePage}
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repositories.SessionRepository
import services.{AuditService, TradeReportingExtractsService}
import utils.UserAnswerHelper
import utils.DateTimeFormats.localDateToInstant
import utils.json.OptionalLocalDateReads.*

import scala.collection.mutable.ListBuffer
import java.time.{LocalDate, ZoneOffset}
import scala.concurrent.{ExecutionContext, Future}
import java.time.Instant

class EditThirdPartySubmissionHandler @Inject (
  identify: IdentifierAction,
  val controllerComponents: MessagesControllerComponents,
  getData: DataRetrievalAction,
  requireData: DataRequiredAction,
  tradeReportingExtractsService: TradeReportingExtractsService,
  userAnswerHelper: UserAnswerHelper,
  sessionRepository: SessionRepository,
  auditService: AuditService
)(implicit val ec: ExecutionContext)
    extends BaseController
    with I18nSupport {

  def submit(thirdPartyEori: String): Action[AnyContent] =
    (identify andThen getData andThen requireData).async { implicit request =>
      tradeReportingExtractsService.getThirdPartyDetails(request.eori, thirdPartyEori).flatMap { previousDetails =>

        val updatedDetails = ThirdPartyRequest(
          userEORI = request.eori,
          thirdPartyEORI = thirdPartyEori,
          accessStart = localDateToInstant(
            request.userAnswers
              .get(EditThirdPartyAccessStartDatePage(thirdPartyEori))
              .getOrElse(previousDetails.accessStartDate)
          ),
          accessEnd = determineAccessEndDate(thirdPartyEori, request, previousDetails),
          reportDateStart = determineReportDateStart(thirdPartyEori, request, previousDetails),
          reportDateEnd = determineReportDateEnd(thirdPartyEori, request, previousDetails),
          accessType = request.userAnswers
            .get(EditThirdPartyDataTypesPage(thirdPartyEori))
            .map(_.map(_.toString.toUpperCase()))
            .getOrElse(previousDetails.dataTypes),
          referenceName =
            request.userAnswers.get(EditThirdPartyReferencePage(thirdPartyEori)).orElse(previousDetails.referenceName)
        )

        (for {
          _             <- tradeReportingExtractsService.editThirdPartyRequest(updatedDetails)
          _             <- auditService
                             .auditThirdPartyUpdated(
                               buildThirdPartyUpdatedEvent(request.eori, thirdPartyEori, previousDetails, updatedDetails)
                             )
                             .recover { case ex =>
                               logger.warn(s"Audit failed for third party edit: ${ex.getMessage}", ex)
                               ()
                             }
          updatedAnswers = userAnswerHelper.removeEditThirdPartyAnswersForEori(thirdPartyEori, request.userAnswers)
          _             <- sessionRepository.set(updatedAnswers)
        } yield Redirect(controllers.thirdparty.routes.ThirdPartyUpdatedConfirmationController.onPageLoad()))
          .recoverWith { case ex =>
            sessionRepository
              .set(userAnswerHelper.removeEditThirdPartyAnswersForEori(thirdPartyEori, request.userAnswers))
              .flatMap(_ =>
                Future.successful(
                  Redirect(
                    controllers.problem.routes.EditThirdPartySubmissionProblemController.onPageLoad(thirdPartyEori)
                  )
                )
              )
          }
      }
    }

  private def determineAccessEndDate(
    thirdPartyEori: String,
    request: DataRequest[AnyContent],
    previousDetails: ThirdPartyDetails
  ) =
    (
      request.userAnswers.get(EditThirdPartyAccessEndDatePage(thirdPartyEori)),
      previousDetails.accessEndDate
    ) match {
      case (Some(value), _) if value == LocalDate.MAX =>
        None
      case (Some(value), _)                           => Some(localDateToInstant(value))
      case (None, Some(prevValue))                    => Some(localDateToInstant(prevValue))
      case (_, _)                                     => None
    }

  private def determineReportDateStart(
    thirdPartyEori: String,
    request: DataRequest[AnyContent],
    previousDetails: ThirdPartyDetails
  ) =
    (
      request.userAnswers.get(EditDeclarationDatePage(thirdPartyEori)),
      previousDetails.dataStartDate,
      request.userAnswers.get(EditDataStartDatePage(thirdPartyEori))
    ) match {
      case (Some(DeclarationDate.AllAvailableData), _, _) => None
      case (_, _, Some(value))                            => Some(localDateToInstant(value))
      case (_, Some(prevValue), _)                        => Some(localDateToInstant(prevValue))
      case (_, _, _)                                      => None
    }

  private def determineReportDateEnd(
    thirdPartyEori: String,
    request: DataRequest[AnyContent],
    previousDetails: ThirdPartyDetails
  ) =
    (
      request.userAnswers.get(EditDataEndDatePage(thirdPartyEori)),
      previousDetails.dataEndDate,
      request.userAnswers.get(EditDeclarationDatePage(thirdPartyEori))
    ) match {
      case (_, _, Some(DeclarationDate.AllAvailableData))      =>
        None
      case (Some(Some(value)), _, _) if value == LocalDate.MAX =>
        None
      case (Some(Some(value)), _, _)                           => Some(localDateToInstant(value))
      case (None, Some(prevValue), _)                          => Some(localDateToInstant(prevValue))
      case (_, _, _)                                           => None
    }

  private def buildThirdPartyUpdatedEvent(
    requesterEori: String,
    thirdPartyEori: String,
    previousDetails: ThirdPartyDetails,
    updatedDetails: ThirdPartyRequest
  ): ThirdPartyUpdatedEvent = {

    val updates = ListBuffer[DataUpdate]()
    addUpdateIfChanged(
      updates,
      "accessType",
      formatAccessType(previousDetails.dataTypes),
      formatAccessType(updatedDetails.accessType)
    )

    addUpdateIfChanged(
      updates,
      "referenceName",
      previousDetails.referenceName.getOrElse(""),
      updatedDetails.referenceName.getOrElse("")
    )

    addUpdateIfChanged(
      updates,
      "thirdPartyAccessStart",
      formatLocalDateAsInstant(previousDetails.accessStartDate),
      updatedDetails.accessStart.toString
    )

    addUpdateIfChanged(
      updates,
      "thirdPartyAccessEnd",
      formatDateAsInstant(previousDetails.accessEndDate),
      formatInstantAsString(updatedDetails.accessEnd)
    )

    val previousAllData = previousDetails.dataStartDate.isEmpty && previousDetails.dataEndDate.isEmpty
    val newAllData      = updatedDetails.reportDateStart.isEmpty && updatedDetails.reportDateEnd.isEmpty
    addUpdateIfChanged(updates, "thirdPartyGivenAccessAllData", previousAllData.toString, newAllData.toString)

    addUpdateIfChanged(
      updates,
      "thirdPartyDataStart",
      formatDataDate(previousDetails.dataStartDate),
      formatInstantAsDataString(updatedDetails.reportDateStart)
    )

    addUpdateIfChanged(
      updates,
      "thirdPartyDataEnd",
      formatDataDate(previousDetails.dataEndDate),
      formatInstantAsDataString(updatedDetails.reportDateEnd)
    )

    ThirdPartyUpdatedEvent(
      requesterEori = requesterEori,
      thirdPartyEori = thirdPartyEori,
      updatesToThirdPartyData = updates.toList
    )
  }

  private def addUpdateIfChanged(
    updates: ListBuffer[DataUpdate],
    fieldName: String,
    previousValue: String,
    newValue: String
  ): Unit =
    if (previousValue != newValue) {
      updates += DataUpdate(fieldName, previousValue, newValue)
    }

  private def formatAccessType(dataTypes: Set[String]): String =
    dataTypes match {
      case types if types.contains("EXPORT") && types.contains("IMPORT") => "import, export"
      case types if types.contains("EXPORT")                             => "export"
      case _                                                             => "import"
    }

  private def formatLocalDateAsInstant(localDate: LocalDate): String =
    localDate.atStartOfDay().toInstant(ZoneOffset.UTC).toString

  private def formatDateAsInstant(dateOpt: Option[LocalDate]): String =
    dateOpt match {
      case Some(endDate) => formatLocalDateAsInstant(endDate)
      case None          => "indefinite"
    }

  private def formatInstantAsString(instantOpt: Option[Instant]): String =
    instantOpt match {
      case Some(endDate) => endDate.toString
      case None          => "indefinite"
    }

  private def formatDataDate(dateOpt: Option[LocalDate]): String =
    dateOpt match {
      case Some(startDate) => formatLocalDateAsInstant(startDate)
      case None            => "all available data"
    }

  private def formatInstantAsDataString(instantOpt: Option[Instant]): String =
    instantOpt match {
      case Some(startDate) => startDate.toString
      case None            => "all available data"
    }

}
