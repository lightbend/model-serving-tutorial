# Hands-on Machine Learning with Kafka-based Streaming Pipelines - a Tutorial

[![Join the chat at https://gitter.im/lightbend-model-serving-tutorial/community](https://badges.gitter.im/lightbend-model-serving-tutorial.svg)](https://gitter.im/lightbend-model-serving-tutorial/community?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

> **NOTE:** This code has been tested only with Java 8 and Scala 2.11.12 and Scala 2.12.8 (the default setting). Any other versions of Java will not work. Other versions of Scala 2.11 and 2.12 may work.

[Boris Lublinsky](mailto:boris.lublinsky@lightbend.com) and [Dean Wampler](mailto:dean.wampler@lightbend.com), [Lightbend](https://lightbend.com/lightbend-platform)

* [Strata Data Conference San Jose, Tuesday, March 6, 2019](https://conferences.oreilly.com/strata/strata-ca/public/schedule/detail/63983)
* [Strata Data Conference London, Tuesday, May 22, 2019](https://conferences.oreilly.com/strata/strata-eu/public/schedule/detail/65420)

©Copyright 2017-2019, Lightbend, Inc. Apache 2.0 License. Please use as you see fit, at your own risk, but attribution is requested.

This tutorial provides a hands-on introduction to serving machine learning models in the context of streaming data services written with [Apache Spark](http://spark.apache.org/), [Apache Flink](http://flink.apache.org/), and microservice tools like [Akka Streams](https://doc.akka.io/docs/akka/2.5/stream/). It discusses implementation options like [TensorFlow Serving](https://www.tensorflow.org/serving/), embedded ML libraries, and [Apache Kafka](http://kafka.apache.org/) as the "data backplane" - the conduit between various services, sources, and sinks.

See the companion presentation for the tutorial in the `presentation` folder:

The core "use case" implemented is a stream processing application that also ingests updated parameters for a machine learning model and then uses the model to score the data. Several implementations of this use case are provided, where we compare and contrast the use of Akka Streams, Spark, and Flink. We also show how to support a few common production requirements, such as managing the in-memory state of the application.

First, we will describe how to build and run the applications. Then we will discuss their designs. For reference materials and more information, see the end of this README. This content will be useful when working through the exercises provided, e.g., the _Scaladocs_ for Akka Streams.

## Tutorial Setup

> **Note:** If you are attending this tutorial at a conference, please follow the setup steps _ahead of time_. If you encounter problems, ask for help on the project's [Gitter room](https://gitter.im/lightbend-model-serving-tutorial/community).

### Install the Required Tools

The Java JDK v8 is required. If not already installed, see the instructions [here](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html). Newer versions of the JDK have not been tested.

[SBT](https://www.scala-sbt.org/), the _de facto_ build tool for Scala is used to build the Scala code. You don't need to know much about SBT to use it for our purposes. The SBT build files are configured to download all the required dependencies. Go [here](https://www.scala-sbt.org/download.html) for SBT installation instructions.

We used [IntelliJ IDEA](https://www.jetbrains.com/idea/) for managing and building the code, which can drive SBT. The free Community Edition is sufficient. However, using IntelliJ isn't required. Any favorite IDE, such as Microsoft Visual Studio Code, or an editor environment will do, but you may need to run SBT in a separate command window.

If you use IntelliJ IDEA or another IDE environment, also install the Scala plugin for the IDE. IntelliJ's Scala plugin includes support for SBT (ignore the SBT plugins that are available). Other IDEs might require a separate SBT plugin. Note that the tutorial uses Scala, 2.12.8.

> **Note:** If you encounter class file or byte code errors when attempting to run SBT below, try removing any versions of Scala that are on your `PATH`. You can also try downloading the 2.12.8 version of Scala from [scala-lang.org](https://www.scala-lang.org) and use it as your Scala SDK for the project or in your IDE globally.

If you use IntelliJ, the quickest way to start is to create a new project from the GitHub repository:

1. File > New > Project from Version Control > GitHub
2. Log into your GitHub account
3. Specify the URL https://github.com/lightbend/model-serving-tutorial
4. When the window opens, you'll see a pop-up with a link asking to load the SBT project; do that
5. Accept the defaults for SBT. Use JDK 1.8 if it's not shown as the default
6. Do one build using the SBT command line, discussed next

> **WARNING:** Unfortunately, the IntelliJ build doesn't properly build the `protobuf` project, which is used for encoding and serializing data exchanged between services. So, you must do the following one-time, command-line build:

1. Open an SBT window:
  a. In IntelliJ, open the _sbt shell_ tool window (_View > Tool Windows > sbt shell_)
  b. If using another IDE with integrated SBT support, invoke the SBT shell as appropriate
  c. If using an editor, open a terminal/command window, change to the tutorial directory, run `sbt`
2. Once `sbt` has finished loading, type `package`. (This runs the SBT _package_ task, which compiles the code and builds jar files.)
3. It should end with `[success] Total time: ...` after ~30 seconds
4. Rebuild after making code changes during the tutorial:
  a. In IntelliJ use the _Build_ command as needed or configure it to trigger automatically when files change.
  b. In another IDE use the corresponding build command it provides
  c. If using an editor, use `~package` in your terminal at the `sbt` prompt. (The `~` tells SBT to watch for changed files and rerun the command given when changes are detected.)

> **Note:** There is also an IntelliJ `sbt` tool window that's useful for browsing the project structure, including the defined _tasks_ (commands). You can double click a task to run it.

If you don't have a GitHub account, just download the latest [release](https://github.com/lightbend/model-serving-tutorial/releases) and import the code as an SBT project into your IDE.

In IntelliJ, use these steps:

1. _Import Project_
2. Select the project root directory (i.e., the same as for this README)
3. Select `sbt` as the project type
4. Use the default settings for `sbt`. Use JDK 1.8 if it's not shown as the default
5. Profit!!

## Clean Up, When You're Done

The tutorial writes various files that you might want to delete once you're finished. The following `bash` commands (or similar Windows commands) will do the trick:

```bash
sbt clean
rm -rf tmp checkpoints cpt output
```

If you start the TensorFlow Serving Docker image described below, you'll want to clean it up when you're done:

```bash
docker stop tfserving_wine
docker rm tfserving_wine
```

## SBT Projects

The SBT build is organized into several projects under the `root` project. There are four projects that illustrate model-serving techniques: `akkaserver`, `flinkserver`, `sparkserver`, `tensorflowserver`. There are four supporting projects: `client`, `configuration`, `model`, `protobufs`. Finally, the `data` directory contains all the data and models used. A data set for wine is used.

> **Note:** Suggested exercises are embedded as code comments throughout the source code in the projects. Search for `// Exercise` to find them.

### Supporting Projects

The implementation contains the following supporting projects.

### Protobufs

This is a supporting project defining two [Google Protobuf](https://developers.google.com/protocol-buffers/) schemas - model and Data.

Model is a generic schema that supports many different model implementations. We will use two of them throughout the code:

* [PMML](http://dmg.org/pmml/v4-3/GeneralStructure.html)
* [TensorFlow](https://www.tensorflow.org/).

For TensorFlow, there are two format options for exported (saved) models:

* [Optimized](https://blog.metaflow.fr/tensorflow-how-to-freeze-a-model-and-serve-it-with-a-python-api-d4f3596b3adc)
* [Saved Model Bundle](https://www.tensorflow.org/api_docs/java/reference/org/tensorflow/SavedModelBundle) (the one used by TensorFlow _bundle_).

You can find the code for both, but we will only used optimized models in our examples.

### Client

For all of the code examples we are using [Apache Kafka](https://kafka.apache.org/) to store both data and model streams. The `client` project is used to send both of these streams, simulating some source of raw data and the output of a model-training system, respectively.

This project uses a local, in-memory Kafka server that implements Kafka without the need to download, install, and run Kafka separately on your development machine.

Two applications (i.e., classes with `main` methods) are provided in the `client` project:

* [DataProvider.scala](client/src/main/scala/com/lightbend/modelserving/client/client/DataProvider.scala): Writes (publishes) the data and model parameters to Kafka topics
* [DataReader.scala](client/src/main/scala/com/lightbend/modelserving/client/client/DataReader.scala): Reads (consumes) the entries in the Kafka topics to validate that messages are written correctly

You'll need to run `DataProvider` when running any of the other apps. In IntelliJ, just left click on the file and pick `run`. Other IDEs work similarly.

If you are using SBT in a terminal, use the following convention that specifies which SBT project you want to run apps from, the `client` in this example. Then select the executable to run from the list presented:

```
sbt:model-serving-tutorial> client/run

Multiple main classes detected, select one to run:

 [1] com.lightbend.modelserving.client.client.DataProvider
 [2] com.lightbend.modelserving.client.client.DataReader

Enter number: 1
...
```

If you know the fully qualified name of a specific executable, for example the `DataProvider` just shown, you can run it using `runMain`:

```
sbt:model-serving-tutorial> client/runMain com.lightbend.modelserving.client.client.DataProvider
...
```

> **Notes:**
>
> 1. You will need one terminal for _each_ service executed concurrently, when using SBT like this.
> 2. When an app takes command-line arguments, simply append them to the `project/run` or `project/runMain ...` command

### Configuration

To make sure that the same Kafka brokers and topics are used across all implementations, the `configuration` project contains all of this shared information.

### Model

The `model` project incorporates the basic model and data operations. The implementation is split into two main parts:

* generic base classes and _traits_ that do not depend on the particular wine data and models
* specific implementation for the wine example

## Using External Services for Model Serving

One way to serve models in a streaming pipeline is to use an external service to which you delegate scoring. Here we will show how to use [TensorFlow Serving](https://www.tensorflow.org/tfx/serving/) invoked from an [Akka Streams](https://doc.akka.io/docs/akka/2.5/stream/) microservice.

Alternatively, other options that we won't investigate here include the following:

* [Seldon-core](https://github.com/SeldonIO/seldon-core)
* [NVIDIA Tensor RT](https://docs.nvidia.com/deeplearning/sdk/tensorrt-install-guide/index.html)
* and others

### Using TensorFlow Serving

Running the TensorFlow Docker image is the easiest way to use TensorFlow Serving. The full details are [here](https://medium.com/tensorflow/serving-ml-quickly-with-tensorflow-serving-and-docker-7df7094aa008).

We'll need to pass the location of this tutorial on your machine to the running container, so for convenience, we'll first define an environment variable, `TUTORIAL_HOME` to point to this directory. Edit the following definitions for your actual path, where we show an example with the tutorial in your `$HOME` directory.

For bash:

```bash
export TUTORIAL_HOME=$HOME/model-serving-tutorial
```

For Windows:

```
set TUTORIAL_HOME=%HOME%\model-serving-tutorial
```

Now you can start the image using the following command:

```bash
docker run -p 8501:8501 --name tfserving_wine --mount type=bind,source=$TUTORIAL_HOME/data/saved,target=/models/wine -e MODEL_NAME=wine -t tensorflow/serving
```

Notes:

* On Windows, use `%TUTORIAL_HOME%`.
* Port `8501` is used by the image to serve REST requests. We map it to the local port `8501`.
* `--name tfserving_wine` defines a container name so we can refer to the running container in `docker` commands conveniently.
* `--mount type=bind,source=$TUTORIAL_HOME/data/saved,target=/models/wine` mounts the local location of the model directory `$TUTORIAL_HOME/data/saved` to the container's directory `/models/wine`.
* `-e MODEL_NAME=wine` specifies the model name.
* `-t tensorflow/serving` specifies the image to use, `tensorflow/serving:latest` in our case.

Once the image is up and running, you can visit the available [REST APIs](https://www.tensorflow.org/serving/api_rest), to get information about deployed model, for example:

* http://localhost:8501/v1/models/wine/versions/1 to get the status of the deployed model
* http://localhost:8501/v1/models/wine/versions/1/metadata to get metadata about deployed model.

Rest APIs are also used to serve the model:

```bash
curl -X POST http://localhost:8501/v1/models/wine/versions/1:predict -d '{"signature_name":"predict","instances":[{"inputs":[7.4,0.7,0.0,1.9,0.076,11.0,34.0,0.9978,3.51,0.56,9.4]}]}'
```

This returns the following result:

```json
{
    "predictions": [[1.14877e-09, 3.39649e-09, 1.19725e-08, 0.014344, 0.0618138, 0.689735, 0.304, 0.0153118, 0.000971983]]
}
```

> **Note:** The _Clean Up_ section earlier in this doc shows the `docker` commands to clean up this running image when you're done with it.

### Using TensorFlow Serving Programmatically

The `tensorflowserver` project shows how Akka Streams can leverage TensorFlow Serving REST APIs in an streaming microservice.

There is one application in this project:

* [TFServingModelServer.scala](client/src/main/scala/com/lightbend/modelserving/tensorflowserving/TFServingModelServer.scala): Reads the wine data stream and calls TensorFlow Serving to score the wine records.

Recall that you'll need to run the `client` project `DataProvider` while running any of the other apps, including `TFServingModelServer`. To run `TFServingModelServer` in IntelliJ, just left click on the file and pick `run`. Other IDEs work similarly.

If you are using SBT in a terminal, you'll need a new terminal running SBT to run `TFServingModelServer`, as the first terminal will be "busy" running `DataProvider`.

Since there is only one app in the `tensorflowserver` project, using `tensorflowserver/run` won't prompt for an app to run:

```
sbt:model-serving-tutorial> tensorflowserver/run

[info] Packaging .../model-serving-tutorial/model/target/scala-2.12/model_2.12-0.1.0-SNAPSHOT.jar ...
[info] Packaging .../model-serving-tutorial/tensorflowserver/target/scala-2.12/tensorflowserver_2.12-0.1.0-SNAPSHOT.jar ...
[info] Done packaging.
[info] Done packaging.
[info] Running com.lightbend.modelserving.tensorflowserving.TFServingModelServer
Creating a new Model Server
Akka model server, brokers localhost:9092
0    [ModelServing-akka.kafka.default-dispatcher-6] INFO  org.apache.kafka.clients.consumer.ConsumerConfig  - ConsumerConfig values:
  auto.commit.interval.ms = 5000
  auto.offset.reset = earliest
...
Model serving in 30 ms, with result 6.0 (model TensorFlow Model Serving, data type wine)
Model serving in 49 ms, with result 6.0 (model TensorFlow Model Serving, data type wine)
...
```

The last few lines shown are records being scored and how long it took.

> **Note:** The time shown is a calculated delta between the timestamp in the record and the end of scoring).

For completeness, you can also run the app with the fully-qualified name, using `runMain`:

```
sbt:model-serving-tutorial> tensorflowserver/runMain com.lightbend.modelserving.tensorflowserving.TFServingModelServer
...
```

## Using Dynamically Controlled Streams for Model Serving

_Dynamically Controlled Streams_ is a general pattern where the behavior of the stream processing in changed at runtime. In this case, we change the behavior by updating the model that gets served. The model is effectively the state of the stream. Hence, such an implementation requires stateful stream processing for the main data stream with the state being updated by a second stream, which we'll call the _state update stream_. Both streams are read from the centralized data log containing all of the incoming data and updates from all of the services.

The following image shows the structure:

![Image](images/Dynamically%20controlled%20streams.png).

In this tutorial, we will demonstrate how to implement this approach using a popular streaming library, [Akka Streams](https://doc.akka.io/docs/akka/2.5/stream/), and the streaming services,
[Spark structured Streaming](https://spark.apache.org/docs/latest/structured-streaming-programming-guide.html) and [Flink](https://flink.apache.org/).

### Akka Streams Implementation

The `akkaserver` project contains the implementation that uses [Akka Streams](https://doc.akka.io/docs/akka/2.5/stream/), along with [Akka Actors](https://doc.akka.io/docs/akka/current/typed/guide/actors-motivation.html#why-modern-systems-need-a-new-programming-model) and [Akka HTTP](https://doc.akka.io/docs/akka-http/current/),
for implementing model serving using the Dynamically Controlled Stream pattern.

The [Alpakka Kafka connector](https://github.com/akka/alpakka-kafka) is used to connect to Kafka to the Akka Streams.

Akka Actors are used to implement the execution state. Specifically, the new [Akka Typed](https://doc.akka.io/docs/akka/current/typed/index.html) API is used. The class [TypedMessages](akkaserver/src/main/scala/com/lightbend/modelserving/akka/TypedMessages.scala) contains definitions of the messages used for Actors and Actor's types. We are using here two actors:

* [ModelServerBehavior](akkaserver/src/main/scala/com/lightbend/modelserving/akka/ModelServerBehavior.scala), which implements the actual model serving, leveraging classes in the `model` project, and manages the state, which is the model itself.
* [ModelServerManagerBehavior](akkaserver/src/main/scala/com/lightbend/modelserving/akka/ModelServerManagerBehavior.scala), which manages `ModelServer` classes, creating an instance of an Akka Actor for every data type, and provides a single entry point for processing models and data.

Additionally we implement the [Queryable State Pattern](https://kafka.apache.org/10/documentation/streams/developer-guide/interactive-queries.html), which provides convenient REST access to the internal state of the stream without the need to write that state to a persistent store and query it from there. Specifically, our implementation, [QueriesAkkaHTTPResource](akkaserver/src/main/scala/com/lightbend/modelserving/akka/QueriesAkkaHttpResource.scala), provides access to the model serving statistics stored in the Actors.

Finally the Akka Streams implementation itself, [AkkaModelServer](akkaserver/src/main/scala/com/lightbend/modelserving/akka/AkkaModelServer.scala) brings all the pieces together and provides the app you run. Running it will
produce a [ServingResult](model/src/main/scala/com/lightbend/modelserving/model/ ServingResult.scala) that contains the result, the duration (execution time) and other information. The duration is a time from message submission to the point when a result can be used. So it includes message submission and Kafka time in addition to the actual model serving latency.

> **Note:** Because the duration is computed with the message submission time, when one of the model server apps is started and it begins processing messages that have been in the Kafka data topic for a while, the duration times computed will be very large. As the app catches up with the latest messages, these times will converge to a lower limit.

There is one application in this project:

* [AkkaModelServer.scala](client/src/main/scala/com/lightbend/modelserving/akka/AkkaModelServer.scala): Ingests the wine data stream and the model updates. Scores the wine records in memory.

> **Note:** Recall that you'll need to run the `client` project `DataProvider` while running this app. Also, you can't run any other of the serving apps at the same time, because they are all part of the same Kafka Consumer Group and there is only one partition for the data. So, only the first app running will receive any data!

To run `AkkaModelServer` in IntelliJ, just left click on the file and pick `run`. Other IDEs work similarly.

If you are using SBT in a terminal, you'll need a new terminal running SBT to run `AkkaModelServer`, as the first terminal will be "busy" running `DataProvider`.

Since there is only one app in the `akkaserver` project, using `akkaserver/run` won't prompt for an app to run:

```
sbt:model-serving-tutorial> akkaserver/run

...
Updated model: ModelToServe(winequalityDesisionTreeRegression,generated from SparkML,2,[B@11e6ac4d,null,wine)
Model serving in 30 ms, with result 6.0 (model tensorflow saved model, data type wine)
Updated model: ModelToServe(winequalityMultilayerPerceptron,generated from SparkML,2,[B@79be7f2a,null,wine)
Model serving in 49 ms, with result 6.0 (model TensorFlow saved model, data type wine)
...
```

The few lines shown indicate when new model parameters are read and when records are scored.

For completeness, you can also run the app with the fully-qualified name, using `runMain`:

```
sbt:model-serving-tutorial> akkaserver/runMain com.lightbend.modelserving.akka.AkkaModelServer
...
```

How do the scoring times compare with the TensorFlow Serving times? We shouldn't read too much into the numbers, because we're not running production services on production hardware, but still...

### Flink Implementation

This implementation shows how to use Apache Flink's [low level joins](https://ci.apache.org/projects/flink/flink-docs-stable/dev/stream/operators/process_function.html) for implementing model serving leveraging the dynamically controlled stream pattern discussed previously.

There are two implementations:

* [ModelServingKeyedJob.scala](flinkserver/src/main/scala/com/lightbend/modelserving/flink/wine/server/ModelServingKeyedJob.scala) uses a key-based implementation, [DataProcessorKeyed.scala](flinkserver/src/main/scala/com/lightbend/modelserving/flink/keyed/DataProcessorKeyed.scala) based on Flink's [Processor Function](https://ci.apache.org/projects/flink/flink-docs-release-1.7/dev/stream/operators/process_function.html#low-level-joins).
* [ModelServingFlatJob](flinkserver/src/main/scala/com/lightbend/modelserving/flink/wine/server/ModelServingFlatJob.scala) uses a partition-based implementation, [DataProcessorMap.scala](flinkserver/src/main/scala/com/lightbend/modelserving/flink/partitioned/DataProcessorMap.scala) based on Flink's [RichCoFlatMapFunction](https://www.da-platform.com/blog/bettercloud-dynamic-alerting-apache-flink).

The choice of implementation depends on the load and the distribution of the data types. See this [Flink documentation on model serving](https://cwiki.apache.org/confluence/display/FLINK/FLIP-23+-+Model+Serving) for more details.

As before, make sure you are running `DataProvider` and don't run any of the other serving applications.

To run one of the model-serving implementations in an IDE, just right-click on the file or code and run it.

If you are using SBT in a terminal, use the following command:

```
sbt:model-serving-tutorial> flinkserver/run

Multiple main classes detected, select one to run:

 [1] com.lightbend.modelserving.flink.wine.query.ModelStateQueryJob
 [2] com.lightbend.modelserving.flink.wine.server.ModelServingFlatJob
 [3] com.lightbend.modelserving.flink.wine.server.ModelServingKeyedJob

Enter number:
...
```

Try 2 or 3. (We'll discuss `ModelStateQueryJob` below.) You'll see log output similar to what we saw previously for the other servers.

> **Notes:**
>
> 1. `ModelServingKeyedJob` also writes the results to a file: `./output/flink-keyed.txt`.
> 2. How do the duration times vary with different model types?

You can also use `runMain` as discussed before, for example:

```
sbt:model-serving-tutorial> flinkserver/runMain com.lightbend.modelserving.flink.wine.server.ModelServingKeyedJob
...
```

Queryable State is implemented for `ModelServingKeyedJob` (but not for `ModelServingFlatJob`) by [ModelStateQueryJob.scala](com.lightbend.modelserving.flink.wine.query.ModelStateQueryJob). For this to work, `ModelStateQueryJob` needs to know the Flink job ID for the running `ModelServingKeyedJob` instance. By default, `ModelServingKeyedJob` writes this ID to a file, `./ModelServingKeyedJob.id`, which `ModelStateQueryJob` reads at startup. You can also override how `ModelStateQueryJob` gets the ID with several command line options (using yet another SBT window), as shown here if you pass the `--help` option:

```bash
sbt:model-serving-tutorial> flinkserver/runMain com.lightbend.modelserving.flink.wine.query.ModelStateQueryJob --help

Usage: ModelStateQueryJob [-h|--help] [-f|--file id_file_path] [-i|--id id]
Where:
-h | --help               Show this help and exit
-f | --file id_file_path  Read the ID from this file. Defaults to "./ModelServingKeyedJob.id"
-i | --id id              Use this ID

So, the default behavior is to read the ID from the default file.

sbt:model-serving-tutorial>
```

With no options or using the `--id` or `--file` options, you'll see output like this:

```text
Using job ID: ...
| Name                                               | Description                            | Since               | Average  | Min | Max |
| -------------------------------------------------- | -------------------------------------- | ------------------- | -------- | --- | --- |
| winequalityGeneralizedLinearRegressionGaussian     | generated from SparkML                 | 2019/01/28 15:01:09 |  0.26531 |   0 |   5 |
| data/optimized_WineQuality                         | generated from TensorFlow              | 2019/01/28 15:01:16 |  6.62500 |   0 |  50 |
| tensorflow saved model                             | generated from TensorFlow saved bundle | 2019/01/28 15:01:47 |  3.22222 |   0 |  27 |
| ... |
```

### Spark Structured Streaming implementation

This implementation shows how to use [Spark Structured Streaming](https://spark.apache.org/docs/latest/structured-streaming-programming-guide.html) to implement model serving, also leveraging the dynamically controlled stream pattern discussed previously. Two implementations are provided:

* [SparkStructuredModelServer.scala](sparkserver/src/main/scala/com/lightbend/modelserving/spark/server/SparkStructuredModelServer.scala) uses _unions_ (suggested by the older [Spark Streaming documentation](https://spark.apache.org/docs/latest/streaming-programming-guide.html)) to join the data and model streams. Then it uses [`mapGroupsWithState`](https://spark.apache.org/docs/latest/structured-streaming-programming-guide.html#arbitrary-stateful-operations) to score the data in the combined stream. This approach requires usage of Spark mini-batching, which is sub optimal for model serving due to the higher latency required for mini-batches.
* [SparkStructuredStateModelServer](sparkserver/src/main/scala/com/lightbend/modelserving/spark/server/SparkStructuredStateModelServer.scala) (suggested by our Lightbend colleague [Gerard Maas](https://www.linkedin.com/in/gerardmaas/)) avoids this drawback by keeping the data and model streams separate, allowing us to use Spark's [Low Latency Continuous Processing](https://databricks.com/blog/2018/03/20/low-latency-continuous-processing-mode-in-structured-streaming-in-apache-spark-2-3-0.html), which enables real-time model serving. The model stream is processed using the older [Spark Streaming](https://spark.apache.org/docs/latest/streaming-programming-guide.html), RDD-based `DStream` API, as it provides some extra programming flexibility that we need (but see the embedded exercise). The data stream is processed with the newer [Spark Structured Streaming](https://spark.apache.org/docs/latest/structured-streaming-programming-guide.html) `Dataset` API.

The second approach offers both lower-latency scoring and a significantly simpler implementation.

As before, make sure you are running `DataProvider` and don't run any of the other serving applications.

To run one of the model-serving implementations in an IDE, just right-click on the file or code and run it.

If you are using SBT in a terminal, use the following command:

```
sbt:model-serving-tutorial> sparkserver/run

Multiple main classes detected, select one to run:

 [1] com.lightbend.modelserving.spark.server.SparkStructuredModelServer
 [2] com.lightbend.modelserving.spark.server.SparkStructuredStateModelServer

Enter number:
...
```

> **Notes:**
>
> 1. While either job is running, open and browse the driver's web console: http://localhost:4040.
> 2. After each of the apps have caught up to the latest messages (see note above), how do the duration times compare for the two apps?
> 3. How do the duration times compare with different model types?

You can also use `runMain` as discussed before, for example:

```
sbt:model-serving-tutorial> sparkserver/runMain com.lightbend.modelserving.spark.server.SparkStructuredStateModelServer
...
```

## References

Our previous tutorial, [github.com/lightbend/kafka-with-akka-streams-kafka-streams-tutorial](https://github.com/lightbend/kafka-with-akka-streams-kafka-streams-tutorial), provides a more general introduction to writing streaming data systems using Akka Streams and Kafka Streams, also with Kafka as the "data backplane". The sample application is also a model-serving example.

### Scala

* [Scala language web site](https://www.scala-lang.org/)
* [Scala library docs ("Scaladocs")](https://www.scala-lang.org/api/current/index.html)

### Kafka

* [Kafka](https://kafka.apache.org/)
* [Kafka Documentation](https://kafka.apache.org/documentation/)

### Spark

For the version of Spark we use, 2.4.0, the latest at this time. Replace `2.4.0` in the URLs with `latest` for the "latest" version of Spark, whatever it is.

* [Spark Home](https://spark.apache.org/) (for all versions)
* [Spark Docs Overview](https://spark.apache.org/docs/2.4.0/index.html)
* [Spark Structured Streaming Programming Guide](https://spark.apache.org/docs/2.4.0/structured-streaming-programming-guide.html)
* [Spark Scaladocs](https://spark.apache.org/docs/2.4.0/api/scala/index.html#org.apache.spark.package)

### Flink

We're using version 1.7.X.

* [Flink Home](https://flink.apache.org/)
* [Flink Docs Overview](https://ci.apache.org/projects/flink/flink-docs-release-1.7/)
* [Flink Scaladocs](https://ci.apache.org/projects/flink/flink-docs-release-1.7/api/scala/index.html#org.apache.flink.api.scala.package) - does not cover the whole Flink API, so also see...
* [Flink Javadocs](https://ci.apache.org/projects/flink/flink-docs-release-1.7/api/java/)


### Akka and Akka Streams

* [Akka](https://akka.io)
* [Akka Documentation](https://akka.io/docs)
* Akka Streams (Scala):
    * [Reference](https://doc.akka.io/docs/akka/current/stream/index.html?language=scala)
    * [Scaladocs](https://doc.akka.io/api/akka/current/akka/stream/index.html)
* Akka Streams (Java):
    * [Reference](https://doc.akka.io/docs/akka/current/stream/index.html?language=java)
    * [Javadocs](https://doc.akka.io/japi/akka/current/index.html?akka/stream/package-summary.html)
* Miscellaneous:
    * [Colin Breck's blog](http://blog.colinbreck.com/), such as his two-part series on integrating Akka Streams and Akka Actors: [Part I](http://blog.colinbreck.com/integrating-akka-streams-and-akka-actors-part-i/), [Part II](http://blog.colinbreck.com/integrating-akka-streams-and-akka-actors-part-ii/)
    * [Akka Team Blog](https://akka.io/blog/)

### Lightbend Platform

Lightbend Platform is an integrated and commercially supported platform for streaming data and microservices, including Apache Kafka, Apache Spark, Apache Flink, Akka Streams, and Kafka Streams, plus developer tools and production monitoring and management tools. Please visit https://www.lightbend.com/lightbend-platform for more information.
