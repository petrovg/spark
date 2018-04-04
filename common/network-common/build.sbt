name := "spark-network-common"
organization := "co.supsoft"
scalaVersion := "2.12.4"
version := "2.4.0-SNAPSHOT"

val slf4jVersion = "1.7.16"

libraryDependencies ++= Seq(

    "io.netty" % "netty-all" % "4.1.17.Final",
    "org.apache.commons" % "commons-lang3" % "3.5",
    "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8",
    "com.fasterxml.jackson.core" % "jackson-databind" % "2.6.7.1",
    "com.fasterxml.jackson.core" % "jackson-annotations" % "2.6.5",
    "io.dropwizard.metrics" % "metrics-core" % "3.1.5",
  
    "org.slf4j" % "slf4j-api" % "1.7.16" % Provided,
    "com.google.code.findbugs" % "jsr305" % "1.3.9" % Provided,
    "com.google.guava" % "guava" % "14.0.1" % Compile,
    "org.apache.commons" % "commons-crypto" % "1.0.0",

    "junit" % "junit" % "4.12" % Test,
    "log4j" % "log4j" % "1.2.17" % Test,
    "co.supsoft" %% "spark-tags" % "2.4.0-SNAPSHOT" % Test,
    "org.slf4j" % "slf4j-log4j12" % slf4jVersion % Test,
    "org.mockito" % "mockito-core" % "1.10.19" % Test


    )
