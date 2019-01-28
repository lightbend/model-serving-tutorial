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

package com.lightbend.modelserving.flink.query

import com.lightbend.modelserving.model.ModelToServeStats
import org.apache.flink.api.common.state.ValueStateDescriptor
import org.apache.flink.api.common.typeinfo.BasicTypeInfo
import org.apache.flink.api.common.{ExecutionConfig, JobID}
import org.apache.flink.api.scala.createTypeInformation
import org.apache.flink.queryablestate.client.QueryableStateClient
import org.joda.time.DateTime

/**
  * ModelStateQuery - query model state (works only for keyed implementation).
  */
object ModelStateQuery {

  // Default Timeout between queries
  val defaulttimeInterval = 1000 * 20        // 20 sec

  /**
    * Main method. When using make sure that you set job ID correctly
    *
    */
  def query(job: String, keys: Seq[String], host: String = "127.0.0.1", port: Int = 9069,
            timeInterval: Long=defaulttimeInterval): Unit = {

    // JobID, has to correspond to a running job
    val jobId = JobID.fromHexString(job)
    // Client
    val client = new QueryableStateClient(host, port)

    // the state descriptor of the state to be fetched.
    val descriptor = new ValueStateDescriptor[ModelToServeStats](
      "currentModel",   // state name
      createTypeInformation[ModelToServeStats].createSerializer(new ExecutionConfig)
    )
    // Key type
    val keyType = BasicTypeInfo.STRING_TYPE_INFO

    // Sample output line, used to compute the following format string:
    // | winequalityGeneralizedLinearRegressionGaussian | generated from SparkML | 2019/01/28 13:01:61 | 0.3157894736842105 |  0 | 4 |
    val format       = "| %-50s | %-25s | %-19s | %8.5f | %3d | %3d |\n"
    val headerFormat = "| %-50s | %-25s | %-19s | %-8s | %-3s | %-3s |\n"
    printf(headerFormat, "Name", "Description", "Since", "Average", "Min", "Max")
    printf(headerFormat, "-" * 50, "-" * 25, "-" * 19, "-" * 8, "-" * 3, "-" * 3)
    while(true) {
      for (key <- keys) {
        // For every key
        try {
          // Get statistics
          val future = client.getKvState(jobId, "currentModelState", key, keyType, descriptor)
          val stats = future.join().value()
          printf(format, stats.name, stats.description,
            new DateTime(stats.since).toString("yyyy/MM/dd HH:MM:SS"),
            stats.duration/stats.usage, stats.min, stats.max)
        }
        catch {case e: Exception => e.printStackTrace()}
      }
      // Wait for next
      Thread.sleep(timeInterval)
    }
  }
}
