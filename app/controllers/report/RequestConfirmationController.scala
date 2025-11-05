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

import config.FrontendAppConfig
import controllers.actions.*
import models.report.{EmailSelection, ReportConfirmation, SubmissionMeta}
import models.requests.DataRequest
import pages.report.{EmailSelectionPage, NewEmailNotificationPage}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.ReportHelpers
import views.html.report.RequestConfirmationView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class RequestConfirmationController @Inject() (
  override val messagesApi: MessagesApi,
  identify: IdentifierAction,
  getData: DataRetrievalAction,
  requireData: DataRequiredAction,
  val controllerComponents: MessagesControllerComponents,
  view: RequestConfirmationView,
  config: FrontendAppConfig
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  def onPageLoad: Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    val additionalEmailList = fetchUpdatedData(request)
    val surveyUrl           = config.exitSurveyUrl
    val isMoreThanOneReport = ReportHelpers.isMoreThanOneReport(request.userAnswers)

    val submissionMeta = request.userAnswers.submissionMeta
      .map(_.as[SubmissionMeta])
      .getOrElse(SubmissionMeta(Seq.empty, "", "", ""))

    Future.successful(
      Ok(
        view(
          additionalEmailList,
          isMoreThanOneReport,
          trasnformReportConfirmations(submissionMeta.reportConfirmations),
          surveyUrl,
          submissionMeta.notificationEmail,
          submissionMeta.submittedDate,
          submissionMeta.submittedTime
        )
      )
    )
  }

  private def trasnformReportConfirmations(reportConfirmations: Seq[ReportConfirmation]): Seq[ReportConfirmation] =
    reportConfirmations.map { rc =>
      val newType = rc.reportType match {
        case "importItem"    => "reportTypeImport.importItem"
        case "importHeader"  => "reportTypeImport.importHeader"
        case "importTaxLine" => "reportTypeImport.importTaxLine"
        case "exportItem"    => "reportTypeImport.exportItem"
        case _               => ""
      }
      rc.copy(reportType = newType)
    }

  private def fetchUpdatedData(request: DataRequest[AnyContent]): Option[String] =
    request.userAnswers.get(EmailSelectionPage).toSeq.flatMap { selected =>
      selected.map {
        case EmailSelection.AddNewEmailValue =>
          request.userAnswers
            .get(NewEmailNotificationPage)
            .map(HtmlFormat.escape(_).toString)
            .getOrElse("")
        case email                           =>
          HtmlFormat.escape(email).toString
      }
    } match {
      case Nil    => None
      case emails => Some(emails.mkString(", "))
    }
}
