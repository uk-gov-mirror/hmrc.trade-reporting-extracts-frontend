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
import controllers.BaseController
import controllers.actions.{DataRetrievalOrCreateAction, IdentifierAction}
import models.report.ReportRequestSection
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import views.html.report.ReportGuidanceView
import models.{AlreadySubmittedFlag, NormalMode}
import repositories.SessionRepository
import services.TradeReportingExtractsService

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ReportGuidanceController @Inject() (
  identify: IdentifierAction,
  getOrCreate: DataRetrievalOrCreateAction,
  view: ReportGuidanceView,
  sessionRepository: SessionRepository,
  val controllerComponents: MessagesControllerComponents,
  appConfig: FrontendAppConfig,
  tradeReportingExtractsService: TradeReportingExtractsService
)(implicit ec: ExecutionContext)
    extends BaseController {

  def onPageLoad: Action[AnyContent] = (identify andThen getOrCreate).async { implicit request =>
    val initialPage          = ReportRequestSection().initialPage
    val JourneyRecoveryUrl   = controllers.problem.routes.JourneyRecoveryController.onPageLoad().url
    val checkYourAnswersUrl  = controllers.report.routes.CheckYourAnswersController.onPageLoad().url
    val alreadySubmittedFlag = request.userAnswers.get(AlreadySubmittedFlag()).getOrElse(false)
    val redirectUrl          =
      if (appConfig.thirdPartyEnabled) {
        controllers.report.routes.ChooseEoriController.onPageLoad(NormalMode).url
      } else {
        controllers.report.routes.DecisionController.onPageLoad(NormalMode).url
      }
    request.userAnswers.get(ReportRequestSection().sectionNavigation).getOrElse(initialPage.url) match {
      case url if url == JourneyRecoveryUrl || (url == checkYourAnswersUrl && alreadySubmittedFlag) =>
        for {
          reportRequestNumberLimit <- tradeReportingExtractsService.getReportRequestLimitNumber
          answers                  <- Future.fromTry(request.userAnswers.remove(AlreadySubmittedFlag()))
          clearedAnswers            = ReportRequestSection.removeAllReportRequestAnswersAndNavigation(answers)
          clearedWithMeta           = clearedAnswers.copy(submissionMeta = None)
          _                        <- sessionRepository.set(clearedWithMeta)
          email                    <- tradeReportingExtractsService.getNotificationEmail(request.eori).map(_.address)
        } yield Ok(view(NormalMode, reportRequestNumberLimit, redirectUrl, email))

      case initialPage.url =>
        for {
          reportRequestNumberLimit <- tradeReportingExtractsService.getReportRequestLimitNumber
          answers                  <- Future.fromTry(request.userAnswers.remove(AlreadySubmittedFlag()))
          clearedWithMeta           = answers.copy(submissionMeta = None)
          _                        <- sessionRepository.set(clearedWithMeta)
          email                    <- tradeReportingExtractsService.getNotificationEmail(request.eori).map(_.address)
        } yield Ok(view(NormalMode, reportRequestNumberLimit, redirectUrl, email))

      case _ =>
        Future.successful(Redirect(ReportRequestSection().navigateTo(request.userAnswers)))
    }
  }
}
