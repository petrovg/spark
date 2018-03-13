name := "spark-launcher"
organization := "co.supsoft"
scalaVersion := "2.12.4"
version := "2.4.0-SNAPSHOT"

val slf4jVersion = "1.7.16"

libraryDependencies ++= Seq(

    "log4j" % "log4j" % "1.2.17" % Test,
    "org.mockito" % "mockito-core" % "1.10.19" % Test,
    "org.slf4j" % "jul-to-slf4j" % slf4jVersion % Test,
    "org.slf4j" % "slf4j-api" % slf4jVersion % Test,
    "org.slf4j" % "slf4j-log4j12" % slf4jVersion % Test,

    "co.supsoft" %% "spark-tags" % "2.4.0-SNAPSHOT"

    )
