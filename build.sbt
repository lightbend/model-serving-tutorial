/*
 * Copyright (C) 2017-2019  Lightbend
 *
 * This file is part of the Lightbend model-serving-tutorial (https://github.com/lightbend/model-serving-tutorial)
 *
 * The model-serving-tutorial is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License Version 2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

name := "model-serving-tutorial"

version := "0.1.0"

scalaVersion in ThisBuild := "2.12.8" // "2.11.12"
scalacOptions in ThisBuild ++= Seq(
  "-feature",
  "-unchecked",
  "-language:higherKinds",
  "-language:postfixOps",
  "-deprecation")

lazy val protobufs = (project in file("./protobufs"))
  .settings(
    PB.targets in Compile := Seq(
      scalapb.gen() -> (sourceManaged in Compile).value))

lazy val configuration = (project in file("./configuration"))

lazy val client = (project in file("./client"))
  .settings(libraryDependencies ++= Dependencies.clientDependencies)
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

lazy val tensorflowserver = (project in file("./tensorflowserver"))
  .settings(libraryDependencies ++= Dependencies.akkaServerDependencies)
  .settings(libraryDependencies ++= Seq(Dependencies.gson))
  .dependsOn(model, configuration)

lazy val root = (project in file(".")).
  aggregate(protobufs, client, model, configuration, flinkserver, sparkserver, akkaserver, tensorflowserver)
