/*
 * Copyright (C) 2019  Lightbend
 *
 * This file is part of ModelServing-tutorial
 *
 * ModelServing-tutorial is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.lightbend.modelserving.flink.wine.query

import com.lightbend.modelserving.flink.query.ModelStateQuery

/**
  * ModelStateQueryJob - query model state (works only for keyed implementation).
  */
object ModelStateQueryJob {

  /**
    *  Main mmethod.
    *  Make sure you use the right jobID here
    *
    */
  def main(args: Array[String]): Unit = {
    ModelStateQuery.query("75a9ada1cdd36d57bf0180809e180f46", Seq("wine"))
  }
}
