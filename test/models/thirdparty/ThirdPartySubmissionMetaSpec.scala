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

package models.thirdparty

import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.{JsSuccess, JsValue, Json}

class ThirdPartySubmissionMetaSpec extends AnyFreeSpec with Matchers {

  "ThirdPartySubmissionMeta" - {

    "must serialise to JSON" in {
      val model = ThirdPartySubmissionMeta(
        thirdPartyEori = "GB123456789000",
        companyName = Some("Test Company"),
        submittedDate = "2025-11-06"
      )

      val json: JsValue = Json.toJson(model)

      (json \ "thirdPartyEori").as[String] mustEqual "GB123456789000"
      (json \ "companyName").asOpt[String].value mustEqual "Test Company"
      (json \ "submittedDate").as[String] mustEqual "2025-11-06"
    }

    "must deserialise from JSON" in {
      val json = Json.obj(
        "thirdPartyEori" -> "GB123456789000",
        "companyName"    -> "Test Company",
        "submittedDate"  -> "2025-11-06"
      )

      val result = json.validate[ThirdPartySubmissionMeta]
      result mustBe a[JsSuccess[_]]
      result.get.thirdPartyEori mustEqual "GB123456789000"
      result.get.companyName.value mustEqual "Test Company"
      result.get.submittedDate mustEqual "2025-11-06"
    }

    "must handle missing optional companyName" in {
      val json = Json.obj(
        "thirdPartyEori" -> "GB123456789000",
        "submittedDate"  -> "2025-11-06"
      )

      val result = json.validate[ThirdPartySubmissionMeta]
      result mustBe a[JsSuccess[_]]
      result.get.companyName mustBe None
      result.get.thirdPartyEori mustEqual "GB123456789000"
    }
  }
}
