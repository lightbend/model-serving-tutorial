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

package com.lightbend.modelserving.tensorflowserving

import akka.actor.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer

import scala.concurrent.Future
import scala.util.{Failure, Success}
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.google.gson.Gson
import com.lightbend.modelserving.model.{ModelToServeStats, ServingResult}

/**
  * This actor forwards requests to score records to TensorFlow Serving
  */
class TFModelServerBehaviour(context: ActorContext[TFModelServerActor]) extends AbstractBehavior[TFModelServerActor] {

  var currentState = new ModelToServeStats("TensorFlow Model Serving",  "TensorFlow Model Serving")
  val gson = new Gson

  println(s"Creating a new Model Server")

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext = system.dispatcher


  /**
    * When passed a record, it creates a request to pass over HTTP to TensorFlow Serving to score the record.
    * A handler is set up to process the result when returned to the Future. If successful, the result is packaged
    * and returned to the sender. The other supported msg is a request for the current state. Note also how errors
    * are handled.
    * @param msg
    */
  override def onMessage(msg: TFModelServerActor): Behavior[TFModelServerActor] = {
    msg match {
      case record : ServeData => // Serve data
        // Create request
        val input = Input(Array(
          record.record.fixedAcidity,
          record.record.volatileAcidity,
          record.record.citricAcid,
          record.record.residualSugar,
          record.record.chlorides,
          record.record.freeSulfurDioxide,
          record.record.totalSulfurDioxide,
          record.record.density,
          record.record.pH,
          record.record.sulphates,
          record.record.alcohol
        ))
        val request = Request("predict", Seq(input).toArray)

        // Post request
        //
        // Exercise:
        // When you run this application {@link TFServingModelServer}, note how long it takes to score records. You should
        // see the times start out fairly large, 100s-1000s of milliseconds, then drop to ~5-100 milliseconds.
        // Recall the example `curl` command in the project README looked something like this:
        //   curl -X POST url -d '{...,"instances":[{"inputs":[...]}]}'
        // Note that you pass a JSON array of "instances". Try modifying the code to pass several records at a time,
        // rather than one at a time. How long does the block take to be scored, compared to the same number of individual
        // invocations? You can modify the time duration logic below to either cover the invocation of the web service or
        // "start the clock" when a first message for a block arrives. The latter takes into account the delay we are
        // imposing while we wait for a block's work of records to arrive.
        //
        // Exercise:
        // One potential problem with the following code is that it uses default connection pool settings,
        // such as the maximum number of retries, etc. This is controlled by a default value for the argument
        // `settings: ConnectionPoolSettings` that `singleRequest` accepts. The default behavior is normally fine, but if
        // if you need tighter control, you can pass your own `ConnectionPoolSettings` object with non-default behavior.
        // To see what to try, select `singleRequest` and use <command>-b (Mac) or <control>-b (Windows & Linux) to
        // navigate to the implementation and then to the `ConnectionPoolSettings` object.

        val start = System.currentTimeMillis()
        val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(
          method = HttpMethods.POST,
          uri = "http://localhost:8501/v1/models/wine/versions/1:predict",
          entity = HttpEntity(ContentTypes.`application/json`, gson.toJson(request))
        ))

        // Get Result
        responseFuture
          .onComplete {
            case Success(res) =>
              Unmarshal(res.entity).to[String].map(pString => {
                val prediction = gson.fromJson(pString, classOf[Prediction]).predictions(0).toSeq
                val quality = prediction.indices.maxBy(prediction)
                // Update state
                currentState = currentState.incrementUsage(System.currentTimeMillis() - start)
                // result
                record.reply ! Some(ServingResult("TensorFlow Model Serving", "wine", record.record.ts, quality))
              })
            case Failure(_)   => sys.error("something wrong")
              record.reply ! None
          }
      case getState : GetState => // State query
        getState.reply ! currentState
    }
    this
  }
}

// Case classes for json mapping

case class Input(inputs : Array[Double])

case class Request(signature_name : String, instances : Array[Input])

case class Prediction(predictions : Array[Array[Double]])
