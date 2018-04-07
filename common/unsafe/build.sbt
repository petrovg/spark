name := "spark-unsafe"
organization := "co.supsoft"
scalaVersion := "2.12.4"
version := "2.4.0-SNAPSHOT"

val slf4jVersion = "1.7.16"

libraryDependencies ++= Seq(

    "co.supsoft" %% "spark-tags" % "2.4.0-SNAPSHOT",
    "com.twitter"  %% "chill" % "0.8.4",

    "com.google.code.findbugs" % "jsr305" % "1.3.9",
    "com.google.guava" % "guava" % "14.0.1",

    "org.slf4j" % "slf4j-api" % slf4jVersion % Provided,

    "junit" % "junit" % "4.12" % Test,
    "org.scalatest" %% "scalatest" % "3.0.3" % Test,
    "org.mockito" % "mockito-core" % "1.10.19" % Test,
    "org.scalacheck" %% "scalacheck" % "1.13.5" % Test,
    "org.apache.commons" % "commons-lang3" % "3.5" % Test )
