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

package org.apache.spark.deploy

import java.io._
import java.lang.reflect.{InvocationTargetException, Modifier, UndeclaredThrowableException}
import java.net.URL
import java.security.PrivilegedExceptionAction
import java.text.ParseException

import scala.annotation.tailrec
import scala.collection.mutable.{ArrayBuffer, HashMap, Map}
import scala.util.{Properties, Try}

import org.apache.commons.lang3.StringUtils
import org.apache.hadoop.conf.{Configuration => HadoopConfiguration}
import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.hadoop.security.UserGroupInformation
import org.apache.hadoop.yarn.conf.YarnConfiguration
import org.apache.ivy.Ivy
import org.apache.ivy.core.LogOptions
import org.apache.ivy.core.module.descriptor._
import org.apache.ivy.core.module.id.{ArtifactId, ModuleId, ModuleRevisionId}
import org.apache.ivy.core.report.ResolveReport
import org.apache.ivy.core.resolve.ResolveOptions
import org.apache.ivy.core.retrieve.RetrieveOptions
import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.plugins.matcher.GlobPatternMatcher
import org.apache.ivy.plugins.repository.file.FileRepository
import org.apache.ivy.plugins.resolver.{ChainResolver, FileSystemResolver, IBiblioResolver}

import org.apache.spark._
import org.apache.spark.api.r.RUtils
import org.apache.spark.deploy.rest._
import org.apache.spark.internal.Logging
import org.apache.spark.internal.config._
import org.apache.spark.launcher.SparkLauncher
import org.apache.spark.util._

/**
 * Main gateway of launching a Spark application.
 *
 * This program handles setting up the classpath with relevant Spark dependencies and provides
 * a layer over the different cluster managers and deploy modes that Spark supports.
 */
object SparkSubmit extends CommandLineUtils with Logging {

  import DependencyUtils._

  // scalastyle:off println
  private[spark] def printVersionAndExit(): Unit = {
    printStream.println("""Welcome to
      ____              __
     / __/__  ___ _____/ /__
    _\ \/ _ \/ _ `/ __/  '_/
   /___/ .__/\_,_/_/ /_/\_\   version %s
      /_/
                        """.format(SPARK_VERSION))
    printStream.println("Using Scala %s, %s, %s".format(Properties.versionString, Properties.javaVmName,
      Properties.javaVersion))
    printStream.println("Branch %s".format(SPARK_BRANCH))
    printStream.println("Compiled by user %s on %s".format(SPARK_BUILD_USER, SPARK_BUILD_DATE))
    printStream.println("Revision %s".format(SPARK_REVISION))
    printStream.println("Url %s".format(SPARK_REPO_URL))
    printStream.println("Type --help for more information.")
    exitFn(0)
  }
  // scalastyle:on println

  override def main(args: Array[String]): Unit = {
    // Initialize logging if it hasn't been done yet. Keep track of whether logging needs to
    // be reset before the application starts.
    val uninitLog = initializeLogIfNecessary(true, silent = true)

    val appArgs = new SparkSubmitArguments(args)
    if (appArgs.verbose) {
      // scalastyle:off println
      printStream.println(appArgs)
      // scalastyle:on println
    }
    appArgs.action match {
      case "submit" => submit(appArgs, uninitLog)
      case "kill" => kill(appArgs)
      case "status" => requestStatus(appArgs)
    }
  }

  /**
   * Kill an existing submission using the REST protocol. Standalone and Mesos cluster mode only.
   */
  private def kill(args: SparkSubmitArguments): Unit = {
    new RestSubmissionClient(args.master)
      .killSubmission(args.submissionToKill)
  }

  /**
   * Request the status of an existing submission using the REST protocol.
   * Standalone and Mesos cluster mode only.
   */
  private def requestStatus(args: SparkSubmitArguments): Unit = {
    new RestSubmissionClient(args.master)
      .requestSubmissionStatus(args.submissionToRequestStatusFor)
  }

  /**
   * Submit the application using the provided parameters.
   *
   * This runs in two steps. First, we prepare the launch environment by setting up
   * the appropriate classpath, system properties, and application arguments for
   * running the child main class based on the cluster manager and the deploy mode.
   * Second, we use this launch environment to invoke the main method of the child
   * main class.
   */
  private def submit(args: SparkSubmitArguments, uninitLog: Boolean): Unit = {
    val (childArgs, childClasspath, sparkConf, childMainClass) = prepareSubmitEnvironment(args)

    def doRunMain(): Unit = {
      if (args.proxyUser != null) {
        val proxyUser = UserGroupInformation.createProxyUser(args.proxyUser,
          UserGroupInformation.getCurrentUser())
        try {
          proxyUser.doAs(new PrivilegedExceptionAction[Unit]() {
            override def run(): Unit = {
              runMain(childArgs, childClasspath, sparkConf, childMainClass, args.verbose)
            }
          })
        } catch {
          case e: Exception =>
            // Hadoop's AuthorizationException suppresses the exception's stack trace, which
            // makes the message printed to the output by the JVM not very helpful. Instead,
            // detect exceptions with empty stack traces here, and treat them differently.
            if (e.getStackTrace().length == 0) {
              // scalastyle:off println
              printStream.println(s"ERROR: ${e.getClass().getName()}: ${e.getMessage()}")
              // scalastyle:on println
              exitFn(1)
            } else {
              throw e
            }
        }
      } else {
        runMain(childArgs, childClasspath, sparkConf, childMainClass, args.verbose)
      }
    }

    // Let the main class re-initialize the logging system once it starts.
    if (uninitLog) {
      Logging.uninitialize()
    }

    doRunMain()
  }

  /**
   * Prepare the environment for submitting an application.
   *
   * @param args the parsed SparkSubmitArguments used for environment preparation.
   * @param conf the Hadoop Configuration, this argument will only be set in unit test.
   * @return a 4-tuple:
   *        (1) the arguments for the child process,
   *        (2) a list of classpath entries for the child,
   *        (3) a map of system properties, and
   *        (4) the main class for the child
   *
   * Exposed for testing.
   */
  private[deploy] def prepareSubmitEnvironment(
      args: SparkSubmitArguments)
      : (Seq[String], Seq[String], SparkConf, String) = {
    // Return values
    val childArgs = new ArrayBuffer[String]()
    val childClasspath = new ArrayBuffer[String]()
    val sparkConf = new SparkConf()
    var childMainClass = args.mainClass

    args.sparkProperties.foreach { case (k, v) => sparkConf.set(k, v) }
    val targetDir = Utils.createTempDir()

    // Resolve glob path for different resources.
    args.jars = Option(args.jars).map(resolveGlobPaths).orNull
    args.files = Option(args.files).map(resolveGlobPaths).orNull

    lazy val secMgr = new SecurityManager(sparkConf)

    // In client mode, download remote files.
    var localPrimaryResource: Option[String] = Option(args.primaryResource).map {
      downloadFile(_, targetDir, sparkConf, secMgr)
    }
    var localJars: Option[String] = Option(args.jars).map {
      downloadFileList(_, targetDir, sparkConf, secMgr)
    }
    var localPyFiles: Option[String] = Option(args.pyFiles).map {
      downloadFileList(_, targetDir, sparkConf, secMgr)
    }

    // Special flag to avoid deprecation warnings at the client
    sys.props("SPARK_SUBMIT") = "true"

    // Map props to deployment conf

    sparkConf.set("spark.master", args.master)
    sparkConf.set("spark.app.name", args.name)
    sparkConf.setOptional("spark.jars.ivy", args.ivyRepoPath)
    sparkConf.setOptional("spark.driver.memory", args.driverMemory)
    sparkConf.setOptional("spark.driver.extraClassPath", args.driverExtraClassPath)
    sparkConf.setOptional("spark.driver.extraJavaOptions", args.driverExtraJavaOptions)
    sparkConf.setOptional("spark.driver.extraLibraryPath", args.driverExtraLibraryPath)

    sparkConf.setOptional("spark.executor.cores", args.executorCores)
    sparkConf.setOptional("spark.executor.memory", args.executorMemory)
    sparkConf.setOptional("spark.cores.max", args.totalExecutorCores)
    sparkConf.setOptional("spark.files", args.files)
    sparkConf.setOptional("spark.jars", args.jars)
    sparkConf.setOptional("spark.driver.memory", args.driverMemory)
    sparkConf.setOptional("spark.cores", args.driverCores)
    localJars.foreach( sparkConf.set("spark.repl.local.jars", _) )

    // We'll mostly be using shells...
    sparkConf.set(UI_SHOW_CONSOLE_PROGRESS, true)

    // If a primary resource jar is given, add it to the Spark jars
    log.info(s"Adding primary resource ${args.primaryResource} in front of spark.jars")
    sparkConf.set("spark.jars", (args.primaryResource :: sparkConf.getOption("spark.jars").
      map(x => x.split(",").toList).getOrElse(List.empty)).mkString(","))

    // Load any properties specified through --conf and the default properties file
    // TODO Do we need this? Can we get rid of it???
    for ((k, v) <- args.sparkProperties) {
      sparkConf.setIfMissing(k, v)
    }

    // Resolve paths in certain spark properties
    val pathConfigs = Seq(
      "spark.jars",
      "spark.files")
    pathConfigs.foreach { config =>
      // Replace old URIs with resolved URIs, if they exist
      sparkConf.getOption(config).foreach { oldValue =>
        sparkConf.set(config, Utils.resolveURIs(oldValue))
      }
    }

    (childArgs, childClasspath, sparkConf, childMainClass)
  }


  /**
   * Run the main method of the child class using the provided launch environment.
   *
   * Note that this main class will not be the one provided by the user if we're
   * running cluster deploy mode or python applications.
   */
  private def runMain(
      childArgs: Seq[String],
      childClasspath: Seq[String],
      sparkConf: SparkConf,
      childMainClass: String,
      verbose: Boolean): Unit = {
    // scalastyle:off println
    if (verbose) {
      printStream.println(s"Main class:\n$childMainClass")
      printStream.println(s"Arguments:\n${childArgs.mkString("\n")}")
      // sysProps may contain sensitive information, so redact before printing
      printStream.println(s"Spark config:\n${Utils.redact(sparkConf.getAll.toMap).mkString("\n")}")
      printStream.println(s"Classpath elements:\n${childClasspath.mkString("\n")}")
      printStream.println("\n")
    }
    // scalastyle:on println

    val loader =
      if (sparkConf.get(DRIVER_USER_CLASS_PATH_FIRST)) {
        new ChildFirstURLClassLoader(new Array[URL](0),
          Thread.currentThread.getContextClassLoader)
      } else {
        new MutableURLClassLoader(new Array[URL](0),
          Thread.currentThread.getContextClassLoader)
      }
    Thread.currentThread.setContextClassLoader(loader)

    for (jar <- childClasspath) {
      addJarToClasspath(jar, loader)
    }

    var mainClass: Class[_] = null

    try {
      mainClass = Utils.classForName(childMainClass)
    } catch {
      case e: ClassNotFoundException =>
        e.printStackTrace(printStream)
        throw new Exception(s"Could not find main class with name $childMainClass", e)
    }

    val app: SparkApplication = if (classOf[SparkApplication].isAssignableFrom(mainClass)) {
      mainClass.newInstance().asInstanceOf[SparkApplication]
    } else {
      // SPARK-4170
      if (classOf[scala.App].isAssignableFrom(mainClass)) {
        printWarning("Subclasses of scala.App may not work correctly. Use a main() method instead.")
      }
      new JavaMainApplication(mainClass)
    }

    @tailrec
    def findCause(t: Throwable): Throwable = t match {
      case e: UndeclaredThrowableException =>
        if (e.getCause() != null) findCause(e.getCause()) else e
      case e: InvocationTargetException =>
        if (e.getCause() != null) findCause(e.getCause()) else e
      case e: Throwable =>
        e
    }

    try {
      println(s">>>> STARTING ${childArgs.toArray.mkString(", ")}, ${sparkConf.toDebugString}")
      app.start(childArgs.toArray, sparkConf)
    } catch {
      case t: Throwable =>
        findCause(t) match {
          case SparkUserAppException(exitCode) =>
            System.exit(exitCode)

          case t: Throwable =>
            throw t
        }
    }
  }

  private[deploy] def addJarToClasspath(localJar: String, loader: MutableURLClassLoader) {
    val uri = Utils.resolveURI(localJar)
    uri.getScheme match {
      case "file" | "local" =>
        val file = new File(uri.getPath)
        if (file.exists()) {
          loader.addURL(file.toURI.toURL)
        } else {
          printWarning(s"Local jar $file does not exist, skipping.")
        }
      case _ =>
        printWarning(s"Skip remote jar $uri.")
    }
  }


  /**
   * Merge a sequence of comma-separated file lists, some of which may be null to indicate
   * no files, into a single comma-separated string.
   */
  private[deploy] def mergeFileLists(lists: String*): String = {
    val merged = lists.filterNot(StringUtils.isBlank)
                      .flatMap(_.split(","))
                      .mkString(",")
    if (merged == "") null else merged
  }

}

/** Provides utility functions to be used inside SparkSubmit. */
private[spark] object SparkSubmitUtils {

  // Exposed for testing
  var printStream = SparkSubmit.printStream

  // Exposed for testing.
  // These components are used to make the default exclusion rules for Spark dependencies.
  // We need to specify each component explicitly, otherwise we miss spark-streaming-kafka-0-8 and
  // other spark-streaming utility components. Underscore is there to differentiate between
  // spark-streaming_2.1x and spark-streaming-kafka-0-8-assembly_2.1x
  val IVY_DEFAULT_EXCLUDES = Seq("catalyst_", "core_", "graphx_", "kvstore_", "launcher_", "mllib_",
    "mllib-local_", "network-common_", "network-shuffle_", "repl_", "sketch_", "sql_", "streaming_",
    "tags_", "unsafe_")

  /**
   * Represents a Maven Coordinate
   * @param groupId the groupId of the coordinate
   * @param artifactId the artifactId of the coordinate
   * @param version the version of the coordinate
   */
  private[deploy] case class MavenCoordinate(groupId: String, artifactId: String, version: String) {
    override def toString: String = s"$groupId:$artifactId:$version"
  }

/**
 * Extracts maven coordinates from a comma-delimited string. Coordinates should be provided
 * in the format `groupId:artifactId:version` or `groupId/artifactId:version`.
 * @param coordinates Comma-delimited string of maven coordinates
 * @return Sequence of Maven coordinates
 */
  def extractMavenCoordinates(coordinates: String): Seq[MavenCoordinate] = {
    coordinates.split(",").map { p =>
      val splits = p.replace("/", ":").split(":")
      require(splits.length == 3, s"Provided Maven Coordinates must be in the form " +
        s"'groupId:artifactId:version'. The coordinate provided is: $p")
      require(splits(0) != null && splits(0).trim.nonEmpty, s"The groupId cannot be null or " +
        s"be whitespace. The groupId provided is: ${splits(0)}")
      require(splits(1) != null && splits(1).trim.nonEmpty, s"The artifactId cannot be null or " +
        s"be whitespace. The artifactId provided is: ${splits(1)}")
      require(splits(2) != null && splits(2).trim.nonEmpty, s"The version cannot be null or " +
        s"be whitespace. The version provided is: ${splits(2)}")
      new MavenCoordinate(splits(0), splits(1), splits(2))

    }
  }

  /** Path of the local Maven cache. */
  private[spark] def m2Path: File = {
    if (Utils.isTesting) {
      // test builds delete the maven cache, and this can cause flakiness
      new File("dummy", ".m2" + File.separator + "repository")
    } else {
      new File(System.getProperty("user.home"), ".m2" + File.separator + "repository")
    }
  }

  /**
   * Extracts maven coordinates from a comma-delimited string
   * @param defaultIvyUserDir The default user path for Ivy
   * @return A ChainResolver used by Ivy to search for and resolve dependencies.
   */
  def createRepoResolvers(defaultIvyUserDir: File): ChainResolver = {
    // We need a chain resolver if we want to check multiple repositories
    val cr = new ChainResolver
    cr.setName("spark-list")

    val localM2 = new IBiblioResolver
    localM2.setM2compatible(true)
    localM2.setRoot(m2Path.toURI.toString)
    localM2.setUsepoms(true)
    localM2.setName("local-m2-cache")
    cr.add(localM2)

    val localIvy = new FileSystemResolver
    val localIvyRoot = new File(defaultIvyUserDir, "local")
    localIvy.setLocal(true)
    localIvy.setRepository(new FileRepository(localIvyRoot))
    val ivyPattern = Seq(localIvyRoot.getAbsolutePath, "[organisation]", "[module]", "[revision]",
      "ivys", "ivy.xml").mkString(File.separator)
    localIvy.addIvyPattern(ivyPattern)
    val artifactPattern = Seq(localIvyRoot.getAbsolutePath, "[organisation]", "[module]",
      "[revision]", "[type]s", "[artifact](-[classifier]).[ext]").mkString(File.separator)
    localIvy.addArtifactPattern(artifactPattern)
    localIvy.setName("local-ivy-cache")
    cr.add(localIvy)

    // the biblio resolver resolves POM declared dependencies
    val br: IBiblioResolver = new IBiblioResolver
    br.setM2compatible(true)
    br.setUsepoms(true)
    br.setName("central")
    cr.add(br)

    val sp: IBiblioResolver = new IBiblioResolver
    sp.setM2compatible(true)
    sp.setUsepoms(true)
    sp.setRoot("http://dl.bintray.com/spark-packages/maven")
    sp.setName("spark-packages")
    cr.add(sp)
    cr
  }

  /**
   * Output a comma-delimited list of paths for the downloaded jars to be added to the classpath
   * (will append to jars in SparkSubmit).
   * @param artifacts Sequence of dependencies that were resolved and retrieved
   * @param cacheDirectory directory where jars are cached
   * @return a comma-delimited list of paths for the dependencies
   */
  def resolveDependencyPaths(
      artifacts: Array[AnyRef],
      cacheDirectory: File): String = {
    artifacts.map { artifactInfo =>
      val artifact = artifactInfo.asInstanceOf[Artifact].getModuleRevisionId
      cacheDirectory.getAbsolutePath + File.separator +
        s"${artifact.getOrganisation}_${artifact.getName}-${artifact.getRevision}.jar"
    }.mkString(",")
  }

  /** Adds the given maven coordinates to Ivy's module descriptor. */
  def addDependenciesToIvy(
      md: DefaultModuleDescriptor,
      artifacts: Seq[MavenCoordinate],
      ivyConfName: String): Unit = {
    artifacts.foreach { mvn =>
      val ri = ModuleRevisionId.newInstance(mvn.groupId, mvn.artifactId, mvn.version)
      val dd = new DefaultDependencyDescriptor(ri, false, false)
      dd.addDependencyConfiguration(ivyConfName, ivyConfName + "(runtime)")
      // scalastyle:off println
      printStream.println(s"${dd.getDependencyId} added as a dependency")
      // scalastyle:on println
      md.addDependency(dd)
    }
  }

  /** Add exclusion rules for dependencies already included in the spark-assembly */
  def addExclusionRules(
      ivySettings: IvySettings,
      ivyConfName: String,
      md: DefaultModuleDescriptor): Unit = {
    // Add scala exclusion rule
    md.addExcludeRule(createExclusion("*:scala-library:*", ivySettings, ivyConfName))

    IVY_DEFAULT_EXCLUDES.foreach { comp =>
      md.addExcludeRule(createExclusion(s"org.apache.spark:spark-$comp*:*", ivySettings,
        ivyConfName))
    }
  }

  /**
   * Build Ivy Settings using options with default resolvers
   * @param remoteRepos Comma-delimited string of remote repositories other than maven central
   * @param ivyPath The path to the local ivy repository
   * @return An IvySettings object
   */
  def buildIvySettings(remoteRepos: Option[String], ivyPath: Option[String]): IvySettings = {
    val ivySettings: IvySettings = new IvySettings
    processIvyPathArg(ivySettings, ivyPath)

    // create a pattern matcher
    ivySettings.addMatcher(new GlobPatternMatcher)
    // create the dependency resolvers
    val repoResolver = createRepoResolvers(ivySettings.getDefaultIvyUserDir)
    ivySettings.addResolver(repoResolver)
    ivySettings.setDefaultResolver(repoResolver.getName)
    processRemoteRepoArg(ivySettings, remoteRepos)
    ivySettings
  }

  /**
   * Load Ivy settings from a given filename, using supplied resolvers
   * @param settingsFile Path to Ivy settings file
   * @param remoteRepos Comma-delimited string of remote repositories other than maven central
   * @param ivyPath The path to the local ivy repository
   * @return An IvySettings object
   */
  def loadIvySettings(
      settingsFile: String,
      remoteRepos: Option[String],
      ivyPath: Option[String]): IvySettings = {
    val file = new File(settingsFile)
    require(file.exists(), s"Ivy settings file $file does not exist")
    require(file.isFile(), s"Ivy settings file $file is not a normal file")
    val ivySettings: IvySettings = new IvySettings
    try {
      ivySettings.load(file)
    } catch {
      case e @ (_: IOException | _: ParseException) =>
        throw new SparkException(s"Failed when loading Ivy settings from $settingsFile", e)
    }
    processIvyPathArg(ivySettings, ivyPath)
    processRemoteRepoArg(ivySettings, remoteRepos)
    ivySettings
  }

  /* Set ivy settings for location of cache, if option is supplied */
  private def processIvyPathArg(ivySettings: IvySettings, ivyPath: Option[String]): Unit = {
    ivyPath.filterNot(_.trim.isEmpty).foreach { alternateIvyDir =>
      ivySettings.setDefaultIvyUserDir(new File(alternateIvyDir))
      ivySettings.setDefaultCache(new File(alternateIvyDir, "cache"))
    }
  }

  /* Add any optional additional remote repositories */
  private def processRemoteRepoArg(ivySettings: IvySettings, remoteRepos: Option[String]): Unit = {
    remoteRepos.filterNot(_.trim.isEmpty).map(_.split(",")).foreach { repositoryList =>
      val cr = new ChainResolver
      cr.setName("user-list")

      // add current default resolver, if any
      Option(ivySettings.getDefaultResolver).foreach(cr.add)

      // add additional repositories, last resolution in chain takes precedence
      repositoryList.zipWithIndex.foreach { case (repo, i) =>
        val brr: IBiblioResolver = new IBiblioResolver
        brr.setM2compatible(true)
        brr.setUsepoms(true)
        brr.setRoot(repo)
        brr.setName(s"repo-${i + 1}")
        cr.add(brr)
        // scalastyle:off println
        printStream.println(s"$repo added as a remote repository with the name: ${brr.getName}")
        // scalastyle:on println
      }

      ivySettings.addResolver(cr)
      ivySettings.setDefaultResolver(cr.getName)
    }
  }

  /** A nice function to use in tests as well. Values are dummy strings. */
  def getModuleDescriptor: DefaultModuleDescriptor = DefaultModuleDescriptor.newDefaultInstance(
    ModuleRevisionId.newInstance("org.apache.spark", "spark-submit-parent", "1.0"))

  /**
   * Resolves any dependencies that were supplied through maven coordinates
   * @param coordinates Comma-delimited string of maven coordinates
   * @param ivySettings An IvySettings containing resolvers to use
   * @param exclusions Exclusions to apply when resolving transitive dependencies
   * @return The comma-delimited path to the jars of the given maven artifacts including their
   *         transitive dependencies
   */
  def resolveMavenCoordinates(
      coordinates: String,
      ivySettings: IvySettings,
      exclusions: Seq[String] = Nil,
      isTest: Boolean = false): String = {
    if (coordinates == null || coordinates.trim.isEmpty) {
      ""
    } else {
      val sysOut = System.out
      try {
        // To prevent ivy from logging to system out
        System.setOut(printStream)
        val artifacts = extractMavenCoordinates(coordinates)
        // Directories for caching downloads through ivy and storing the jars when maven coordinates
        // are supplied to spark-submit
        val packagesDirectory: File = new File(ivySettings.getDefaultIvyUserDir, "jars")
        // scalastyle:off println
        printStream.println(
          s"Ivy Default Cache set to: ${ivySettings.getDefaultCache.getAbsolutePath}")
        printStream.println(s"The jars for the packages stored in: $packagesDirectory")
        // scalastyle:on println

        val ivy = Ivy.newInstance(ivySettings)
        // Set resolve options to download transitive dependencies as well
        val resolveOptions = new ResolveOptions
        resolveOptions.setTransitive(true)
        val retrieveOptions = new RetrieveOptions
        // Turn downloading and logging off for testing
        if (isTest) {
          resolveOptions.setDownload(false)
          resolveOptions.setLog(LogOptions.LOG_QUIET)
          retrieveOptions.setLog(LogOptions.LOG_QUIET)
        } else {
          resolveOptions.setDownload(true)
        }

        // Default configuration name for ivy
        val ivyConfName = "default"

        // A Module descriptor must be specified. Entries are dummy strings
        val md = getModuleDescriptor
        // clear ivy resolution from previous launches. The resolution file is usually at
        // ~/.ivy2/org.apache.spark-spark-submit-parent-default.xml. In between runs, this file
        // leads to confusion with Ivy when the files can no longer be found at the repository
        // declared in that file/
        val mdId = md.getModuleRevisionId
        val previousResolution = new File(ivySettings.getDefaultCache,
          s"${mdId.getOrganisation}-${mdId.getName}-$ivyConfName.xml")
        if (previousResolution.exists) previousResolution.delete

        md.setDefaultConf(ivyConfName)

        // Add exclusion rules for Spark and Scala Library
        addExclusionRules(ivySettings, ivyConfName, md)
        // add all supplied maven artifacts as dependencies
        addDependenciesToIvy(md, artifacts, ivyConfName)
        exclusions.foreach { e =>
          md.addExcludeRule(createExclusion(e + ":*", ivySettings, ivyConfName))
        }
        // resolve dependencies
        val rr: ResolveReport = ivy.resolve(md, resolveOptions)
        if (rr.hasError) {
          throw new RuntimeException(rr.getAllProblemMessages.toString)
        }
        // retrieve all resolved dependencies
        ivy.retrieve(rr.getModuleDescriptor.getModuleRevisionId,
          packagesDirectory.getAbsolutePath + File.separator +
            "[organization]_[artifact]-[revision](-[classifier]).[ext]",
          retrieveOptions.setConfs(Array(ivyConfName)))
        resolveDependencyPaths(rr.getArtifacts.toArray, packagesDirectory)
      } finally {
        System.setOut(sysOut)
      }
    }
  }

  private[deploy] def createExclusion(
      coords: String,
      ivySettings: IvySettings,
      ivyConfName: String): ExcludeRule = {
    val c = extractMavenCoordinates(coords)(0)
    val id = new ArtifactId(new ModuleId(c.groupId, c.artifactId), "*", "*", "*")
    val rule = new DefaultExcludeRule(id, ivySettings.getMatcher("glob"), null)
    rule.addConfiguration(ivyConfName)
    rule
  }

}