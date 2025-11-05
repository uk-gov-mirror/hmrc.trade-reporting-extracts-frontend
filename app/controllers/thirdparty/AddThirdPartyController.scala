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

import controllers.BaseController
import controllers.actions.*
import models.AlreadyAddedThirdPartyFlag
import models.thirdparty.AddThirdPartySection
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repositories.SessionRepository
import views.html.thirdparty.AddThirdPartyView

import scala.concurrent.{ExecutionContext, Future}
import javax.inject.Inject

class AddThirdPartyController @Inject() (
  override val messagesApi: MessagesApi,
  identify: IdentifierAction,
  getOrCreate: DataRetrievalOrCreateAction,
  sessionRepository: SessionRepository,
  val controllerComponents: MessagesControllerComponents,
  view: AddThirdPartyView
)(implicit ec: ExecutionContext)
    extends BaseController {

  def onPageLoad: Action[AnyContent] = (identify andThen getOrCreate).async { implicit request =>
    val initialPage                = AddThirdPartySection().initialPage
    val JourneyRecoveryUrl         = controllers.problem.routes.JourneyRecoveryController.onPageLoad().url
    val checkYourAnswersUrl        = controllers.thirdparty.routes.AddThirdPartyCheckYourAnswersController.onPageLoad().url
    val alreadyAddedThirdPartyFlag = request.userAnswers.get(AlreadyAddedThirdPartyFlag()).getOrElse(false)

    request.userAnswers.get(AddThirdPartySection().sectionNavigation).getOrElse(initialPage.url) match {
      case url if url == JourneyRecoveryUrl || (url == checkYourAnswersUrl && alreadyAddedThirdPartyFlag) =>
        for {
          answers        <- Future.fromTry(request.userAnswers.remove(AlreadyAddedThirdPartyFlag()))
          clearedAnswers  = AddThirdPartySection.removeAllAddThirdPartyAnswersAndNavigation(answers)
          clearedWithMeta = clearedAnswers.copy(submissionMeta = None)
          _              <- sessionRepository.set(clearedWithMeta)
        } yield Ok(view())
      case initialPage.url                                                                                =>
        for {
          answers        <- Future.fromTry(request.userAnswers.remove(AlreadyAddedThirdPartyFlag()))
          clearedWithMeta = answers.copy(submissionMeta = None)
          _              <- sessionRepository.set(clearedWithMeta)
        } yield Ok(view())
      case _                                                                                              =>
        Future.successful(Redirect(AddThirdPartySection().navigateTo(request.userAnswers)))
    }
  }
}
