name := "spark-kvstore"
organization := "co.supsoft"
scalaVersion := "2.12.4"
version := "2.4.0-SNAPSHOT"

val slf4jVersion = "1.7.16"

libraryDependencies ++= Seq(

    "co.supsoft" %% "spark-tags" % "2.4.0-SNAPSHOT",

    "com.google.guava" % "guava" % "14.0.1",

    "com.fasterxml.jackson.core" % "jackson-core" % "2.6.7",
    "com.fasterxml.jackson.core" % "jackson-databind" % "2.6.7.1",
    "com.fasterxml.jackson.core" % "jackson-annotations" % "2.6.7",

    "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8",

    "junit" % "junit" % "4.12" % Test,
    "commons-io" % "commons-io" % "2.4" % Test,
    "io.dropwizard.metrics" % "metrics-core" % "3.1.5" % Test,
    "log4j" % "log4j" % "1.2.17" % Test,
    "org.slf4j" % "slf4j-api" % slf4jVersion % Test,
    "org.slf4j" % "slf4j-log4j12" % slf4jVersion % Test

    )
    