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

package models.report

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.{JsSuccess, JsValue, Json}

class SubmissionMetaSpec extends AnyFreeSpec with Matchers {

  "SubmissionMeta" - {

    "must serialise to JSON" in {
      val model = SubmissionMeta(
        reportConfirmations = Seq(ReportConfirmation("reportName", "importItem", "RE0000001")),
        notificationEmail = "test@example.com",
        submittedDate = "2025-11-06",
        submittedTime = "10:00:00"
      )

      val json: JsValue = Json.toJson(model)

      (json \ "notificationEmail").as[String] mustEqual "test@example.com"
      (json \ "submittedDate").as[String] mustEqual "2025-11-06"
      (json \ "submittedTime").as[String] mustEqual "10:00:00"
      (json \ "reportConfirmations").as[Seq[ReportConfirmation]].head.reportReference mustEqual "RE0000001"
    }

    "must deserialise from JSON" in {
      val json = Json.obj(
        "reportConfirmations" -> Seq(
          Json.obj(
            "reportName"      -> "reportName",
            "reportType"      -> "importItem",
            "reportReference" -> "RE0000001"
          )
        ),
        "notificationEmail"   -> "test@example.com",
        "submittedDate"       -> "2025-11-06",
        "submittedTime"       -> "10:00:00"
      )

      val result = json.validate[SubmissionMeta]
      result mustBe a[JsSuccess[_]]
      result.get.notificationEmail mustEqual "test@example.com"
      result.get.reportConfirmations.head.reportReference mustEqual "RE0000001"
      result.get.reportConfirmations.head.reportName mustEqual "reportName"
      result.get.reportConfirmations.head.reportType mustEqual "importItem"
    }
  }
}
