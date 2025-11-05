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

package navigation

import base.SpecBase
import config.FrontendAppConfig
import controllers.report.routes
import models.report.{ChooseEori, Decision, EmailSelection, ReportDateRange}
import models.{CheckMode, NormalMode}
import org.mockito.Mockito.*
import org.scalatestplus.mockito.MockitoSugar
import pages.report.*

class ReportNavigatorSpec extends SpecBase with MockitoSugar {

  val mockAppConfig: FrontendAppConfig = mock[FrontendAppConfig]
  when(mockAppConfig.thirdPartyEnabled).thenReturn(true)
  when(mockAppConfig.notificationsEnabled).thenReturn(true)

  val navigator = new ReportNavigator(mockAppConfig)

  "ReportNavigator" - {

    "in Normal mode" - {

      "navigate from DecisionPage" - {
        "when ChooseEori is Myeori" in {
          val ua = emptyUserAnswers
            .set(DecisionPage, Decision.Import)
            .success
            .value
            .set(ChooseEoriPage, ChooseEori.Myeori)
            .success
            .value
          navigator.nextPage(DecisionPage, NormalMode, ua) mustBe
            controllers.report.routes.EoriRoleController.onPageLoad(NormalMode)
        }

        "when ChooseEori is Myauthority and Decision is Import" in {
          val ua = emptyUserAnswers
            .set(DecisionPage, Decision.Import)
            .success
            .value
            .set(ChooseEoriPage, ChooseEori.Myauthority)
            .success
            .value

          navigator.nextPage(DecisionPage, NormalMode, ua) mustBe
            controllers.report.routes.ReportTypeImportController.onPageLoad(NormalMode)
        }

        "when ChooseEori is Myauthority and Decision is Export" in {
          val ua = emptyUserAnswers
            .set(DecisionPage, Decision.Export)
            .success
            .value
            .set(ChooseEoriPage, ChooseEori.Myauthority)
            .success
            .value

          navigator.nextPage(DecisionPage, NormalMode, ua) mustBe
            controllers.report.routes.ExportItemReportController.onPageLoad()
        }

        "when ChooseEoriPage is missing" in {
          val ua = emptyUserAnswers.set(DecisionPage, Decision.Import).success.value

          navigator.nextPage(DecisionPage, NormalMode, ua) mustBe
            controllers.problem.routes.JourneyRecoveryController.onPageLoad()
        }
      }

      "navigate from ChooseEoriPage" - {
        "to EoriRolePage when Myeori" in {
          val ua = emptyUserAnswers.set(ChooseEoriPage, ChooseEori.Myeori).success.value
          navigator.nextPage(ChooseEoriPage, NormalMode, ua) mustBe routes.DecisionController.onPageLoad(NormalMode)
        }

        "to SelectThirdPartyEoriPage when Myauthority" in {
          val ua = emptyUserAnswers.set(ChooseEoriPage, ChooseEori.Myauthority).success.value
          navigator.nextPage(ChooseEoriPage, NormalMode, ua) mustBe routes.SelectThirdPartyEoriController
            .onPageLoad(NormalMode)
        }
      }

      "navigate from SelectThirdPartyEoriPage" - {
        "to ReportTypeImportPage when DecisionPage is Import" in {
          val ua = emptyUserAnswers.set(DecisionPage, Decision.Import).success.value
          navigator.nextPage(
            SelectThirdPartyEoriPage,
            NormalMode,
            ua
          ) mustBe routes.ReportTypeImportController.onPageLoad(NormalMode)
        }

        "to export-item-report when DecisionPage is Export" in {
          val ua = emptyUserAnswers.set(DecisionPage, Decision.Export).success.value
          navigator.nextPage(
            SelectThirdPartyEoriPage,
            NormalMode,
            ua
          ) mustBe routes.ExportItemReportController.onPageLoad()
        }

        "to DecisionPage when DecisionPage is missing" in {
          val ua = emptyUserAnswers
          navigator.nextPage(
            SelectThirdPartyEoriPage,
            NormalMode,
            ua
          ) mustBe routes.DecisionController.onPageLoad(NormalMode)
        }
      }

      "navigate from EoriRolePage" - {
        "to ReportTypeImportPage when Import" in {
          val ua = emptyUserAnswers.set(DecisionPage, Decision.Import).success.value
          navigator.nextPage(EoriRolePage, NormalMode, ua) mustBe routes.ReportTypeImportController.onPageLoad(
            NormalMode
          )
        }

        "to export-item-report when Export" in {
          val ua = emptyUserAnswers.set(DecisionPage, Decision.Export).success.value
          navigator.nextPage(EoriRolePage, NormalMode, ua) mustBe routes.ExportItemReportController.onPageLoad(
          )
        }
      }

      "navigate from ReportTypeImportPage" - {
        "when a third party journey to request start date" in {
          val ua = emptyUserAnswers.set(ChooseEoriPage, ChooseEori.Myauthority).success.value
          navigator.nextPage(ReportTypeImportPage, NormalMode, ua) mustBe routes.CustomRequestStartDateController
            .onPageLoad(NormalMode)
        }

        "when not a third party journey to report date range" in {
          val ua = emptyUserAnswers.set(ChooseEoriPage, ChooseEori.Myeori).success.value
          navigator.nextPage(ReportTypeImportPage, NormalMode, ua) mustBe routes.ReportDateRangeController
            .onPageLoad(NormalMode)
        }

        "when choose eori not answered and third party flag enabled to journey recovery" in {
          when(mockAppConfig.thirdPartyEnabled).thenReturn(true)
          navigator.nextPage(
            ReportTypeImportPage,
            NormalMode,
            emptyUserAnswers
          ) mustBe controllers.problem.routes.JourneyRecoveryController.onPageLoad()
        }

        "when choose eori not answered and third party flag disabled to report date range" in {
          when(mockAppConfig.thirdPartyEnabled).thenReturn(false)
          navigator.nextPage(ReportTypeImportPage, NormalMode, emptyUserAnswers) mustBe routes.ReportDateRangeController
            .onPageLoad(NormalMode)
        }
      }

      "navigate from ReportDateRangePage" - {
        "to CustomRequestStartDatePage when CustomDateRange" in {
          val ua = emptyUserAnswers.set(ReportDateRangePage, ReportDateRange.CustomDateRange).success.value
          navigator.nextPage(ReportDateRangePage, NormalMode, ua) mustBe routes.CustomRequestStartDateController
            .onPageLoad(NormalMode)
        }

        "to ReportNamePage otherwise" in {
          val ua = emptyUserAnswers.set(ReportDateRangePage, ReportDateRange.LastFullCalendarMonth).success.value
          navigator.nextPage(ReportDateRangePage, NormalMode, ua) mustBe routes.ReportNameController.onPageLoad(
            NormalMode
          )
        }
      }

      "navigate from CustomRequestStartDatePage to CustomRequestEndDatePage" in {
        navigator.nextPage(
          CustomRequestStartDatePage,
          NormalMode,
          emptyUserAnswers
        ) mustBe routes.CustomRequestEndDateController.onPageLoad(NormalMode)
      }

      "navigate from CustomRequestEndDatePage to ReportNamePage" in {
        navigator.nextPage(CustomRequestEndDatePage, NormalMode, emptyUserAnswers) mustBe routes.ReportNameController
          .onPageLoad(NormalMode)
      }

      "navigate from ReportNamePage to MaybeAdditionalEmailPage when notifications enabled" in {
        navigator.nextPage(ReportNamePage, NormalMode, emptyUserAnswers) mustBe routes.MaybeAdditionalEmailController
          .onPageLoad(NormalMode)
      }

      "navigate from MaybeAdditionalEmailPage" - {
        "to EmailSelectionPage when true" in {
          val ua = emptyUserAnswers.set(MaybeAdditionalEmailPage, true).success.value
          navigator.nextPage(MaybeAdditionalEmailPage, NormalMode, ua) mustBe routes.EmailSelectionController
            .onPageLoad(NormalMode)
        }

        "to CheckYourAnswersPage when false" in {
          val ua = emptyUserAnswers.set(MaybeAdditionalEmailPage, false).success.value
          navigator.nextPage(MaybeAdditionalEmailPage, NormalMode, ua) mustBe routes.CheckYourAnswersController
            .onPageLoad()
        }
      }

      "navigate from EmailSelectionPage" - {
        "to NewEmailNotificationPage when Email3 selected" in {
          val ua = emptyUserAnswers.set(EmailSelectionPage, Set(EmailSelection.AddNewEmailValue)).success.value
          navigator.nextPage(EmailSelectionPage, NormalMode, ua) mustBe routes.NewEmailNotificationController
            .onPageLoad(NormalMode)
        }

        "to CheckYourAnswersPage otherwise" in {
          val ua = emptyUserAnswers.set(EmailSelectionPage, Set("test1@example.com")).success.value
          navigator.nextPage(EmailSelectionPage, NormalMode, ua) mustBe routes.CheckYourAnswersController.onPageLoad()
        }
      }

      "navigate from NewEmailNotificationPage to check-new-email" in {
        navigator.nextPage(
          NewEmailNotificationPage,
          NormalMode,
          emptyUserAnswers
        ) mustBe routes.CheckNewEmailController.onPageLoad(NormalMode)
      }

      "navigate from CheckNewEmailPage" - {
        "to CheckYourAnswersPage when true" in {
          val ua = emptyUserAnswers.set(CheckNewEmailPage, true).success.value
          navigator.nextPage(CheckNewEmailPage, NormalMode, ua) mustBe routes.CheckYourAnswersController.onPageLoad()
        }

        "back to MaybeAdditionalEmailPage when false" in {
          val ua = emptyUserAnswers.set(CheckNewEmailPage, false).success.value
          navigator.nextPage(CheckNewEmailPage, NormalMode, ua) mustBe routes.MaybeAdditionalEmailController.onPageLoad(
            NormalMode
          )
        }

        "to JourneyRecovery when missing" in {
          val ua = emptyUserAnswers
          navigator.nextPage(
            CheckNewEmailPage,
            NormalMode,
            ua
          ) mustBe controllers.problem.routes.JourneyRecoveryController.onPageLoad()
        }
      }
    }

    "in Check mode" - {

      "navigate from DecisionPage" - {
        "when ChooseEori is Myeori" in {
          val ua = emptyUserAnswers
            .set(DecisionPage, Decision.Import)
            .success
            .value
            .set(ChooseEoriPage, ChooseEori.Myeori)
            .success
            .value
          navigator.nextPage(DecisionPage, CheckMode, ua) mustBe
            controllers.report.routes.EoriRoleController.onPageLoad(CheckMode)
        }

        "when ChooseEori is Myauthority and Decision is Import" in {
          val ua = emptyUserAnswers
            .set(DecisionPage, Decision.Import)
            .success
            .value
            .set(ChooseEoriPage, ChooseEori.Myauthority)
            .success
            .value

          navigator.nextPage(DecisionPage, CheckMode, ua) mustBe
            controllers.report.routes.ReportTypeImportController.onPageLoad(CheckMode)
        }

        "when ChooseEori is Myauthority and Decision is Export" in {
          val ua = emptyUserAnswers
            .set(DecisionPage, Decision.Export)
            .success
            .value
            .set(ChooseEoriPage, ChooseEori.Myauthority)
            .success
            .value

          navigator.nextPage(DecisionPage, CheckMode, ua) mustBe
            routes.CheckYourAnswersController.onPageLoad()
        }

        "when ChooseEoriPage is missing" in {
          val ua = emptyUserAnswers.set(DecisionPage, Decision.Import).success.value

          navigator.nextPage(DecisionPage, CheckMode, ua) mustBe
            controllers.problem.routes.JourneyRecoveryController.onPageLoad()
        }
      }

      "navigate from ChooseEoriPage" - {
        "to EoriRolePage when Myeori" in {
          val ua = emptyUserAnswers.set(ChooseEoriPage, ChooseEori.Myeori).success.value
          navigator.nextPage(ChooseEoriPage, CheckMode, ua) mustBe routes.DecisionController.onPageLoad(NormalMode)
        }

        "to SelectThirdPartyEoriPage when Myauthority" in {
          val ua = emptyUserAnswers.set(ChooseEoriPage, ChooseEori.Myauthority).success.value
          navigator.nextPage(ChooseEoriPage, CheckMode, ua) mustBe routes.SelectThirdPartyEoriController
            .onPageLoad(NormalMode)
        }
      }

      "navigate from SelectThirdPartyEoriPage" - {
        "to ReportTypeImportPage when DecisionPage is Import" in {
          val ua = emptyUserAnswers.set(DecisionPage, Decision.Import).success.value
          navigator.nextPage(
            SelectThirdPartyEoriPage,
            CheckMode,
            ua
          ) mustBe routes.ReportTypeImportController.onPageLoad(CheckMode)
        }

        "to export-item-report when DecisionPage is Export" in {
          val ua = emptyUserAnswers.set(DecisionPage, Decision.Export).success.value
          navigator.nextPage(
            SelectThirdPartyEoriPage,
            CheckMode,
            ua
          ) mustBe routes.ExportItemReportController.onPageLoad()
        }

        "to DecisionPage when DecisionPage is missing" in {
          val ua = emptyUserAnswers
          navigator.nextPage(
            SelectThirdPartyEoriPage,
            CheckMode,
            ua
          ) mustBe routes.DecisionController.onPageLoad(CheckMode)
        }
      }

      "navigate from EoriRolePage" - {
        "to ReportTypeImportPage when Import" in {
          val ua = emptyUserAnswers.set(DecisionPage, Decision.Import).success.value
          navigator.nextPage(EoriRolePage, CheckMode, ua) mustBe routes.ReportTypeImportController.onPageLoad(CheckMode)
        }

        "to ReportDateRangePage when Export" in {
          val ua = emptyUserAnswers.set(DecisionPage, Decision.Export).success.value
          navigator.nextPage(EoriRolePage, CheckMode, ua) mustBe routes.CheckYourAnswersController.onPageLoad()
        }
      }

      "navigate from ReportTypeImportPage to CheckYourAnswersPage" in {
        navigator.nextPage(ReportTypeImportPage, CheckMode, emptyUserAnswers) mustBe routes.CheckYourAnswersController
          .onPageLoad()
      }

      "navigate from ReportDateRangePage" - {
        "to CustomRequestStartDatePage when CustomDateRange" in {
          val ua = emptyUserAnswers.set(ReportDateRangePage, ReportDateRange.CustomDateRange).success.value
          navigator.nextPage(ReportDateRangePage, CheckMode, ua) mustBe routes.CustomRequestStartDateController
            .onPageLoad(CheckMode)
        }

        "to CheckYourAnswersPage otherwise" in {
          val ua = emptyUserAnswers.set(ReportDateRangePage, ReportDateRange.LastFullCalendarMonth).success.value
          navigator.nextPage(ReportDateRangePage, CheckMode, ua) mustBe routes.CheckYourAnswersController.onPageLoad()
        }
      }

      "navigate from CustomRequestStartDatePage to CustomRequestEndDatePage" in {
        navigator.nextPage(
          CustomRequestStartDatePage,
          CheckMode,
          emptyUserAnswers
        ) mustBe routes.CustomRequestEndDateController.onPageLoad(CheckMode)
      }

      "navigate from CustomRequestEndDatePage to CheckYourAnswersPage" in {
        navigator.nextPage(
          CustomRequestEndDatePage,
          CheckMode,
          emptyUserAnswers
        ) mustBe routes.CheckYourAnswersController.onPageLoad()
      }

      "navigate from ReportNamePage to CheckYourAnswersPage" in {
        navigator.nextPage(ReportNamePage, CheckMode, emptyUserAnswers) mustBe routes.CheckYourAnswersController
          .onPageLoad()
      }

      "navigate from MaybeAdditionalEmailPage" - {
        "to EmailSelectionPage when true" in {
          val ua = emptyUserAnswers.set(MaybeAdditionalEmailPage, true).success.value
          navigator.nextPage(MaybeAdditionalEmailPage, CheckMode, ua) mustBe routes.EmailSelectionController.onPageLoad(
            CheckMode
          )
        }

        "to CheckYourAnswersPage when false" in {
          val ua = emptyUserAnswers.set(MaybeAdditionalEmailPage, false).success.value
          navigator.nextPage(MaybeAdditionalEmailPage, CheckMode, ua) mustBe routes.CheckYourAnswersController
            .onPageLoad()
        }
      }

      "navigate from EmailSelectionPage" - {
        "to NewEmailNotificationPage when Email3 selected" in {
          val ua = emptyUserAnswers.set(EmailSelectionPage, Set(EmailSelection.AddNewEmailValue)).success.value
          navigator.nextPage(EmailSelectionPage, CheckMode, ua) mustBe routes.NewEmailNotificationController.onPageLoad(
            CheckMode
          )
        }

        "to CheckYourAnswersPage otherwise" in {
          val ua = emptyUserAnswers.set(EmailSelectionPage, Set("test1@example.com")).success.value
          navigator.nextPage(EmailSelectionPage, CheckMode, ua) mustBe routes.CheckYourAnswersController.onPageLoad()
        }
      }

      "navigate from NewEmailNotificationPage to check-new-email" in {
        navigator.nextPage(
          NewEmailNotificationPage,
          CheckMode,
          emptyUserAnswers
        ) mustBe routes.CheckNewEmailController.onPageLoad(CheckMode)
      }

      "navigate from CheckNewEmailPage" - {
        "to CheckYourAnswersPage when true" in {
          val ua = emptyUserAnswers.set(CheckNewEmailPage, true).success.value
          navigator.nextPage(CheckNewEmailPage, CheckMode, ua) mustBe routes.CheckYourAnswersController.onPageLoad()
        }

        "back to MaybeAdditionalEmailPage when false" in {
          val ua = emptyUserAnswers.set(CheckNewEmailPage, false).success.value
          navigator.nextPage(CheckNewEmailPage, CheckMode, ua) mustBe routes.MaybeAdditionalEmailController.onPageLoad(
            CheckMode
          )
        }

        "to JourneyRecovery when missing" in {
          val ua = emptyUserAnswers
          navigator.nextPage(
            CheckNewEmailPage,
            CheckMode,
            ua
          ) mustBe controllers.problem.routes.JourneyRecoveryController.onPageLoad()
        }
      }

    }
  }

}
