# Implementing Model Serving - a Tutorial

[![Join the chat at https://gitter.im/kafka-with-akka-streams-kafka-streams-tutorial](https://badges.gitter.im/kafka-with-akka-streams-kafka-streams-tutorial.svg)](https://gitter.im/kafka-with-akka-streams-kafka-streams-tutorial?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

> **NOTE:** This code has been tested only with Java 8 and Scala 2.11.12. Any other versions will not work (scala 2.11.x will probably do fine)

[Boris Lublinsky](mailto:boris.lublinsky@lightbend.com) and [Dean Wampler](mailto:dean.wampler@lightbend.com), [Lightbend](https://lightbend.com/fast-data-platform)

* [Strata Data Conference San Jose, Tuesday, March 6, 2019](https://conferences.oreilly.com/strata/strata-ca/public/schedule/detail/63983)
* [Strata Data Conference London, Tuesday, May 22, 2019](https://conferences.oreilly.com/strata/strata-eu/public/schedule/detail/65420)

©Copyright 2019, Lightbend, Inc. Apache 2.0 License. Please use as you see fit, but attribution is requested.

This tutorial provides an introduction to Model Serving.

See the companion presentation for the tutorial in the `presentation` folder:

The core "use case" implemented is a stream processing application that also ingests updated parameters for a machine learning model and then uses the model to score the data. Several implementations of this use case are provided. 
They not only compare Akka Streams vs. Spark and Flink, but they also show how to support a few other common production requirements, such as managing the in-memory state of the application.

First, we will describe how to build and run the applications. Then we will discuss their designs. For reference materials and more information, see the end of this README.

## Tutorial Setup

> **Note:** If you are attending this tutorial at a conference, please follow the setup steps _ahead of time_. If you encounter problems, ask for help on the project's [Gitter room](https://gitter.im/kafka-with-akka-streams-kafka-streams-tutorial).

### Install the Required Tools

The Java JDK v8 is required. If not already installed, see the instructions [here](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html).

[SBT](https://www.scala-sbt.org/), the _de facto_ build tool for Scala is used to build the code, both the Scala and Java implementations. The SBT build files are configured to download all the required dependencies. Go [here](https://www.scala-sbt.org/download.html) for installation instructions.

We recommend using [IntelliJ IDEA](https://www.jetbrains.com/idea/) for managing and building the code, which can drive SBT. The free Community Edition is sufficient. However, using IntelliJ isn't required; any favorite IDE or editor environment will do; you'll just need to run SBT in a separate command window.

If you use IntelliJ IDEA or another IDE environment, also install the Scala plugin for the IDE. IntelliJ's Scala plugin includes support for SBT (ignore the SBT plugins that are available). Other IDEs might require a separate SBT plugin. Note that the tutorial uses Scala, 2.11.12.

> **Note:** If you encounter class file or byte code errors when attempting to run SBT below, try removing any versions of Scala that are on your `PATH`. You can also try downloading the 2.12.4 version of Scala from [scala-lang.org](https://www.scala-lang.org) and use it as your Scala SDK for the project or in your IDE globally.

If you use IntelliJ, the quickest way to start is to create a new project from the GitHub repository:

1. File > New > Project from Version Control > GitHub
2. Log into your GitHub account
3. Specify the URL https://github.com/lightbend/kafka-with-akka-streams-kafka-streams-tutorial
4. When the window opens, you'll see a pop-up with a link asking to load the SBT project; do that
5. Accept the defaults for SBT. Use JDK 1.8 if it's not shown as the default.
6. Do one build using the SBT command line...

> **WARNING:** Unfortunately, the IntelliJ build doesn't properly build the `protobuf` project (protobuf), which is used for encoding and serializing data exchanged between services. So, you must do the following one-time, command-line build:

1. Open an SBT window:
    a. In IntelliJ, open the _sbt shell_ tool window (_View > Tool Windows > sbt shell_)
    b. If not using IntelliJ, open a terminal/command window, change to the tutorial directory, run `sbt`.
2. Type `package`, once `sbt` has finished loading
3. It should end with `[success] Total time: ...` after ~30 seconds
4. Now just use IntelliJ's _Build_ command as needed or triggered automatically. If not using IntelliJ, use `~package` in your terminal inside `sbt`.

> **Note:** There is also an IntelliJ `sbt` tool window that's useful for browsing the project structure, including the defined _tasks_ (commands). You can double click a task to run it.

If you don't have a GitHub account, just download the latest [release](https://github.com/lightbend/kafka-with-akka-streams-kafka-streams-tutorial/releases) and import the code as an SBT project into your IDE. In IntelliJ, use these steps:

1. _Import Project_
2. Select the project root directory (i.e., the same as for this README)
3. Select `sbt` as the project type
4. Use the default settings for `sbt`. Use JDK 1.8 if it's not shown as the default.
5. Profit!!


## Using Tensorflow serving

The easiest way to use Tensorflow serving is [using Tensorflow Docker image](https://medium.com/tensorflow/serving-ml-quickly-with-tensorflow-serving-and-docker-7df7094aa008).
To do this first pull tensorflow image:
````
docker pull tensorflow/serving
````
Once you have it locally, you can start the image using the following command:
````
docker run -p 8501:8501 --name tfserving_wine --mount type=bind,source=/Users/boris/Projects/model-serving-tutorial/data/saved,target=/models/wine -e MODEL_NAME=wine -t tensorflow/serving
````
Here `8501` is the port used by the image to serve REST request which is mapped to local port `8501`

`--name tfserving_wine` : Creates container name that can be used to refer to the running container by name.
 
`--mount type=bind,source=/Users/boris/Projects/model-serving-tutorial/data/saved,target=/models/wine` mounts local location of the model's directory `/Users/boris/Projects/model-serving-tutorial/data/saved` to container's directory

`-e MODEL_NAME=wine` specifies model's name

`-t tensorflow/serving` specifies image to use, `tensorflow/serving:latest` in our case

Once the image is up and running, you can execute available [REST APIs](https://www.tensorflow.org/serving/api_rest), for example:
````
// 20190114193241
// http://localhost:8501/v1/models/wine/versions/1

{
  "model_version_status": Array[1][
    {
      "version": "1",
      "state": "AVAILABLE",
      "status": {
        "error_code": "OK",
        "error_message": ""
      }
    }
  ]
}
````   
and
````
// 20190114193345
// http://localhost:8501/v1/models/wine/versions/1/metadata

{
  "model_spec": {
    "name": "wine",
    "signature_name": "",
    "version": "1"
  },
  "metadata": {
    "signature_def": {
      "signature_def": {
        "predict": {
          "inputs": {
            "inputs": {
              "dtype": "DT_FLOAT",
              "tensor_shape": {
                "dim": Array[2][
                  {
                    "size": "-1",
                    "name": ""
                  },
                  {
                    "size": "11",
                    "name": ""
                  }
                ],
                "unknown_rank": false
              },
              "name": "dense_1_input:0"
            }
          },
          "outputs": {
            "outputs": {
              "dtype": "DT_FLOAT",
              "tensor_shape": {
                "dim": Array[2][
                  {
                    "size": "-1",
                    "name": ""
                  },
                  {
                    "size": "9",
                    "name": ""
                  }
                ],
                "unknown_rank": false
              },
              "name": "dense_3/Sigmoid:0"
            }
          },
          "method_name": "tensorflow/serving/predict"
        }
      }
    }
  }
}
````
Rest APIs also allow to serve model:
````
curl -X POST http://localhost:8501/v1/models/wine/versions/1:predict -d '{"signature_name":"predict","instances":[{"inputs":[{"dense_1_input":[7.4, 0.7, 0.0, 1.9, 0.076, 11.0, 34.0, 0.9978, 3.51, 0.56, 9.4, 5.0]}]}]}'

````