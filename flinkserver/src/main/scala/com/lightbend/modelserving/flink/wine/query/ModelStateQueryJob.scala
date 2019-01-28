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

  val defaultIDFileName = "./ModelServingKeyedJob.id"

  /**
    * Main method for the query process.
    * The ID of the keyed job is required for this process. There are two ways it
    * it specified:
    * 1. `ModelServingKeyedJob` writes its ID to a file, defaulting to "./ModelServingKeyedJob.id"
    * 2. You specify a different file with the command-line option `--file path`
    * 3. You specify the id itself with the command-line option `--id ID`
    */
  def main(args: Array[String]): Unit = {
    val jobID = determineID(args)
    println(s"Using job ID: $jobID")
    ModelStateQuery.query(job = jobID, keys = Seq("wine"))
  }

  // Ignore trailing arguments
  protected def determineID(args: Seq[String]): String = args match {
    case Nil => readID(defaultIDFileName)
    case ("-h" | "--help") +: tail         => help; sys.exit(0)
    case ("-f" | "--file") +: path +: tail => readID(path)
    case ("-i" | "--id")   +: id +: tail   => id
    case _ => println(s"ERROR: Unrecognized argument(s): ${args.mkString(" ")}"); help; sys.exit(1)
  }
  protected def readID(path: String):String = scala.io.Source.fromFile(path).getLines.next

  protected def help() {
    println("""
      |Usage: ModelStateQueryJob [-h|--help] [-f|--file id_file_path] [-i|--id id]
      |Where:
      |-h | --help               Show this help and exit
      |-f | --file id_file_path  Read the ID from this file. Defaults to "./ModelServingKeyedJob.id"
      |-i | --id id              Use this ID
      |
      |So, the default behavior is to read the ID from the default file.
      |""".stripMargin)
  }
}
