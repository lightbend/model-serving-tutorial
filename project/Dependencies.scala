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

import Versions._
import sbt._

object Dependencies {

  val kafka                 = "org.apache.kafka"        %% "kafka"                              % kafkaVersion
  val curator               = "org.apache.curator"      % "curator-test"                        % curatorVersion                 // ApacheV2
  val commonIO              = "commons-io"              % "commons-io"                          % commonIOVersion

  val tensorflow            = "org.tensorflow"          % "tensorflow"                          % tensorflowVersion
  val tensoeflowProto       = "org.tensorflow"          % "proto"                               % tensorflowVersion
  val PMMLEvaluator         = "org.jpmml"               % "pmml-evaluator"                      % PMMLVersion
  val PMMLExtensions        = "org.jpmml"               % "pmml-evaluator-extension"            % PMMLVersion

  val flinkScala            = "org.apache.flink"        % "flink-scala_2.11"                    % flinkVersion
  val flinkStreaming        = "org.apache.flink"        % "flink-streaming-scala_2.11"          % flinkVersion
  val flinkKafka            = "org.apache.flink"        %% "flink-connector-kafka"              % flinkVersion
  val flinkQueryableRuntime = "org.apache.flink"        %% "flink-queryable-state-runtime"      % flinkVersion
  val flinkQueryableClient  = "org.apache.flink"        %% "flink-queryable-state-client-java"  % flinkVersion
  val joda                  = "joda-time"               % "joda-time"                           % jodaVersion

  val sparkcore             = "org.apache.spark"        %% "spark-core"                         % sparkVersion
  val sparkstreaming        = "org.apache.spark"        %% "spark-streaming"                    % sparkVersion
  val sparkSQLkafka         = "org.apache.spark"        %% "spark-sql-kafka-0-10"               % sparkVersion
  val sparkSQL              = "org.apache.spark"        %% "spark-sql"                          % sparkVersion
  val sparkkafka            = "org.apache.spark"        %% "spark-streaming-kafka-0-10"         % sparkVersion

  val kryo                  = "com.esotericsoftware.kryo" % "kryo"                              % kryoVersion

  val reactiveKafka         = "com.typesafe.akka"       %% "akka-stream-kafka"                  % reactiveKafkaVersion

  val akkaStreamTyped       = "com.typesafe.akka"       %% "akka-stream-typed"                  % akkaVersion
  val akkaHttp              = "com.typesafe.akka"       %% "akka-http"                          % akkaHttpVersion
  val akkaHttpJsonJackson   = "de.heikoseeberger"       %% "akka-http-jackson"                  % akkaHttpJsonVersion
  val akkatyped             = "com.typesafe.akka"       %% "akka-actor-typed"                   % akkaVersion
  
  val gson                  = "com.google.code.gson"    % "gson"                                % gsonVersion

  val modelsDependencies = Seq(PMMLEvaluator, PMMLExtensions, tensorflow, tensoeflowProto)
  val flinkDependencies = Seq(flinkScala, flinkStreaming, flinkKafka, flinkQueryableRuntime, flinkQueryableClient, joda)
  val sparkDependencies = Seq(sparkcore, sparkstreaming, sparkkafka, sparkSQL, sparkSQLkafka)
  val akkaServerDependencies = Seq(reactiveKafka, akkaStreamTyped, akkatyped, akkaHttp, akkaHttpJsonJackson, reactiveKafka)

}