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

package com.lightbend.modelserving.winemodel

import com.lightbend.model.winerecord.WineRecord
import com.lightbend.modelserving.model.DataToServe

import scala.util.Try

// Container for wine data
object DataRecord {

  def fromByteArray(message: Array[Byte]): Try[DataToServe[WineRecord]] = Try {
    DataRecord(WineRecord.parseFrom(message))
  }

  def wineFromByteArray(message: Array[Byte]): Try[WineRecord] = Try {
    WineRecord.parseFrom(message)
  }
}

case class DataRecord(record : WineRecord) extends DataToServe[WineRecord]{
  def getType : String = record.dataType
  def getRecord : WineRecord = record
}