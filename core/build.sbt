name := "spark-core"
organization := "co.supsoft"
scalaVersion := "2.12.4"
version := "2.4.0-SNAPSHOT"

val jettyVersion = "9.4.8.v20171121"
val slf4jVersion = "1.7.16"

val sparkCommonVersion = "2.4.0-SNAPSHOT"

libraryDependencies ++= Seq(

    "org.apache.avro" % "avro" % "1.7.7",
    "org.apache.avro" % "avro-mapred" % "1.7.7" classifier "hadoop2",
    "com.google.guava" % "guava" % "14.0.1" % Provided,
    "com.twitter"  %% "chill" % "0.8.4",
    "com.twitter"  % "chill-java" % "0.8.4",


    "org.scala-lang" % "scala-compiler" % "2.12.4",

    "org.apache.xbean"  % "xbean-asm5-shaded" % "4.4",
    "org.apache.hadoop"  % "hadoop-client" % "2.7.3",

    "co.supsoft" %% "spark-launcher" % sparkCommonVersion,
    "co.supsoft" %% "spark-kvstore" % sparkCommonVersion,
    "co.supsoft" %% "spark-network-common" % sparkCommonVersion,
    "co.supsoft" %% "spark-network-shuffle" % sparkCommonVersion,
    "co.supsoft" %% "spark-unsafe" % sparkCommonVersion,
    "co.supsoft" %% "spark-tags" % sparkCommonVersion,
    "co.supsoft" %% "spark-launcher" % sparkCommonVersion,
    "co.supsoft" %% "spark-tags" % sparkCommonVersion,


    "net.java.dev.jets3t"  % "jets3t" % "0.9.4",
    "org.apache.curator"  % "curator-recipes" % "2.6.0",
    
    "org.eclipse.jetty" % "jetty-plus" % jettyVersion % Compile,
    "org.eclipse.jetty" % "jetty-security" % jettyVersion % Compile,
    "org.eclipse.jetty" % "jetty-util" % jettyVersion % Compile,
    "org.eclipse.jetty" % "jetty-server" % jettyVersion % Compile,
    "org.eclipse.jetty" % "jetty-http" % jettyVersion % Compile,
    "org.eclipse.jetty" % "jetty-continuation" % jettyVersion % Compile,
    "org.eclipse.jetty" % "jetty-servlet" % jettyVersion % Compile,
    "org.eclipse.jetty" % "jetty-proxy" % jettyVersion % Compile,
    "org.eclipse.jetty" % "jetty-client" % jettyVersion % Compile,
    "org.eclipse.jetty" % "jetty-servlets" % jettyVersion % Compile,


    "javax.servlet" % "javax.servlet-api" % "3.1.0",
    "org.apache.commons" % "commons-lang3" % "3.5",
    "org.apache.commons" % "commons-math3" % "3.4.1",
    "com.google.code.findbugs" % "jsr305" % "1.3.9",

    "org.slf4j" % "slf4j-api" % slf4jVersion,
    "org.slf4j" % "jul-to-slf4j" % slf4jVersion,
    "org.slf4j" % "jcl-over-slf4j" % slf4jVersion,
    "log4j" % "log4j" % "1.2.17",
    "org.slf4j" % "slf4j-log4j12" % slf4jVersion,
    
    "com.ning"  % "compress-lzf" % "1.0.3",
    
    "org.xerial.snappy"  % "snappy-java" % "1.1.7.1",
    
    "org.lz4"  % "lz4-java" % "1.4.0",
    
    "com.github.luben" % "zstd-jni" % "1.3.2-2",
    
    "org.roaringbitmap" % "RoaringBitmap" % "0.5.11",
    
    "commons-net" % "commons-net" % "3.1",
    
    "org.json4s"  %% "json4s-jackson" % "3.5.3" excludeAll(ExclusionRule("com.fasterxml.jackson.core")),
    
    "org.glassfish.jersey.core" % "jersey-client"  % "2.22.2",
    "org.glassfish.jersey.core" % "jersey-common" % "2.22.2",
    "org.glassfish.jersey.core" % "jersey-server" % "2.22.2",
    "org.glassfish.jersey.containers" % "jersey-container-servlet" % "2.22.2",
    "org.glassfish.jersey.containers" % "jersey-container-servlet-core" % "2.22.2",
    
    "io.netty" % "netty-all" % "4.1.17.Final",
    "io.netty" % "netty" % "3.9.9.Final",
    
    "com.clearspring.analytics" % "stream" % "2.7.0",
    
    "io.dropwizard.metrics" % "metrics-core" % "3.1.5",
    "io.dropwizard.metrics" % "metrics-jvm" % "3.1.5",
    "io.dropwizard.metrics" % "metrics-json" % "3.1.5",
    "io.dropwizard.metrics" % "metrics-graphite" % "3.1.5",
    
    "com.fasterxml.jackson.core" % "jackson-databind" % "2.6.7.1",
    "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.6.7.1",

    "org.apache.ivy" % "ivy" % "2.4.0",

    "org.apache.hadoop" % "hadoop-common" % "3.1.0" % Runtime,
    "org.apache.hadoop" % "hadoop-aws" % "3.1.0" % Runtime,

    "junit" % "junit" % "4.12" % Test,
    "org.scalatest" %% "scalatest" % "3.0.3" % Test,
    "org.apache.derby" % "derby" % "10.12.1.1" % Test,
    "org.seleniumhq.selenium" % "selenium-java" % "2.52.0" % Test,
    "org.seleniumhq.selenium" % "selenium-htmlunit-driver" % "2.52.0" % Test,
    "xml-apis" % "xml-apis" % "1.4.01" % Test,
    "org.hamcrest" % "hamcrest-core" % "1.3" % Test,
    "org.hamcrest" % "hamcrest-library" % "1.3" % Test,
    "org.mockito" % "mockito-core" % "1.10.19" % Test,
    "org.scalacheck" %% "scalacheck" % "1.13.5" % Test,
    "org.apache.curator" % "curator-test" % "2.6.0" % Test,

    "net.razorvine" % "pyrolite" % "4.13",
    
    "net.sf.py4j" % "py4j" % "0.10.6",
    
    "org.apache.commons" % "commons-crypto" % "1.0.0"

    )
    
