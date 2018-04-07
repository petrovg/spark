/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.repl

import java.io.File
import java.net.{URI, URLClassLoader}
import java.util.Locale

import scala.tools.nsc.GenericRunnerSettings
import org.apache.spark._
import org.apache.spark.internal.Logging
import org.apache.spark.util.Utils

object Main extends Logging {

  initializeLogIfNecessary(true)
  Signaling.cancelOnInterrupt()

  lazy val conf = new SparkConf()

  lazy val rootDir = conf.getOption("spark.repl.classdir").getOrElse(Utils.getLocalDir(conf))

  lazy val outputDir = Utils.createTempDir(root = rootDir, namePrefix = "repl")

  lazy val sparkContext: SparkContext = createSparkContext(conf, outputDir)

  private var hasErrors = false

  private def scalaOptionError(msg: String): Unit = {
    hasErrors = true
    // scalastyle:off println
    Console.err.println(msg)
    // scalastyle:on println
  }

  def main(args: Array[String]): Unit = doMain(args, new SparkILoop)(conf)

  def startShell(args: Array[String])(implicit conf: SparkConf): Unit =
      doMain(args, new SparkILoop())

  // Visible for testing
  private[repl] def doMain(args: Array[String], interp: SparkILoop)(implicit conf: SparkConf): Unit = {

    val jarsOOO = Utils.getLocalUserJarsForShell(conf)
      // Remove file:///, file:// or file:/ scheme if exists for each jar
      .map { x => if (x.startsWith("file:")) new File(new URI(x)).getPath else x }
      .mkString(File.pathSeparator)

    val classLoader: URLClassLoader = this.getClass.getClassLoader.asInstanceOf[URLClassLoader]
    val jars = classLoader.getURLs.mkString(java.io.File.pathSeparator)

    val interpArguments = List(
      "-Yrepl-class-based",
      "-Yrepl-outdir", s"${outputDir.getAbsolutePath}",
      "-classpath", jars
    ) ++ args.toList

    val settings = new GenericRunnerSettings(scalaOptionError)
    settings.processArguments(interpArguments, true)

    if (!hasErrors) {
      interp.process(settings) // Repl starts and goes in loop of R.E.P.L
      Option(sparkContext).foreach(_.stop)
    }
  }

  def createSparkContext(conf: SparkConf, outputDir: File): SparkContext = {
    val execUri = System.getenv("SPARK_EXECUTOR_URI")
    conf.setIfMissing("spark.app.name", "Spark shell")
    // SparkContext will detect this configuration and register it with the RpcEnv's
    // file server, setting spark.repl.class.uri to the actual URI for executors to
    // use. This is sort of ugly but since executors are started as part of SparkContext
    // initialization in certain cases, there's an initialization order issue that prevents
    // this from being set after SparkContext is instantiated.
    conf.set("spark.repl.class.outputDir", outputDir.getAbsolutePath())
    if (execUri != null) {
      conf.set("spark.executor.uri", execUri)
    }
    if (System.getenv("SPARK_HOME") != null) {
      conf.setSparkHome(System.getenv("SPARK_HOME"))
    }

    logInfo("Created Spark context")

    new SparkContext(conf)

  }

}
