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

name := "model-serving-tutorial"

version := "0.1"

scalaVersion in ThisBuild := "2.11.12"

lazy val protobufs = (project in file("./protobufs"))
  .settings(
    PB.targets in Compile := Seq(
      scalapb.gen() -> (sourceManaged in Compile).value
    )  
  )

lazy val configuration = (project in file("./configuration"))

lazy val client = (project in file("./client"))
  .settings(libraryDependencies ++= Seq(Dependencies.kafka, Dependencies.curator) ++ Seq(Dependencies.commonIO))
  .dependsOn(protobufs, configuration)

lazy val model = (project in file("./model"))
  .settings(libraryDependencies ++= Dependencies.modelsDependencies)
  .dependsOn(protobufs)

lazy val flinkserver = (project in file("./flinkserver"))
  .settings(libraryDependencies ++= Dependencies.flinkDependencies)
  .dependsOn(model, configuration)

lazy val sparkserver = (project in file("./sparkserver"))
  .settings(libraryDependencies ++= Dependencies.sparkDependencies)
  .dependsOn(model, configuration)

lazy val akkaserver = (project in file("./akkaserver"))
  .settings(libraryDependencies ++= Dependencies.akkaServerDependencies)
  .dependsOn(model, configuration)

lazy val root = (project in file(".")).
  aggregate(protobufs, client, model, configuration, flinkserver, sparkserver, akkaserver)