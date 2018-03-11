scalaVersion := "2.12.4"



val jettyVersion = "9.3.20.v20170531"

val slf4j.version = "1.7.16"

libraryDependencies ++= Seq(
    "org.apache.avro" % "avro" % "1.7.7"
    "org.apache.avro" % "avro-mapred" % "1.7.7" classifier "hadoop2"
    "com.google.guava" % "guava" % "14.0.1" % Provided
    "com.twitter"  %% "chill" % "0.8.4"
    "com.twitter"  % "chill-java" % "0.8.4"

    "org.apache.xbean"  %% "xbean-asm5-shaded" % "4.4"
    
    "org.apache.hadoop"  %% "hadoop-client" % "2.7.3"
    
    "org.apache.spark"  %% "spark-launcher"
      <version>${project.version}</version>
    
    "org.apache.spark"  %% "spark-kvstore_${scala.binary.version}"
      <version>${project.version}</version>
    
    "org.apache.spark"  %% "spark-network-common_${scala.binary.version}"
      <version>${project.version}</version>
    
    "org.apache.spark"  %% "spark-network-shuffle_${scala.binary.version}"
      <version>${project.version}</version>
    
    "org.apache.spark"  %% "spark-unsafe_${scala.binary.version}"
      <version>${project.version}</version>
    
    "net.java.dev.jets3t"  %% "jets3t"
    
    "org.apache.curator"  %% "curator-recipes"
    

    <!-- Jetty dependencies promoted to compile here so they are shaded
         and inlined into spark-core jar -->
    "org.eclipse.jetty"  %% "jetty-plus" % jettyVersion
    "org.eclipse.jetty"  %% "jetty-security" % jettyVersion
    "org.eclipse.jetty"  %% "jetty-util" % jettyVersion
    "org.eclipse.jetty"  %% "jetty-server" % jettyVersion
    "org.eclipse.jetty"  %% "jetty-http" % jettyVersion
    "org.eclipse.jetty"  %% "jetty-continuation" % jettyVersion
    "org.eclipse.jetty"  %% "jetty-servlet" % jettyVersion
    "org.eclipse.jetty"  %% "jetty-proxy" % jettyVersion
    "org.eclipse.jetty"  %% "jetty-client" % jettyVersion
    "org.eclipse.jetty"  %% "jetty-servlets" % jettyVersion


    "javax.servlet" % "javax.servlet-api" % "3.1.0"
    "org.apache.commons" % "commons-lang3" % "3.5"
    "org.apache.commons" % "commons-math3" % "3.4.1"
    "com.google.code.findbugs" % "jsr305" % "1.3.9"
    
    "org.slf4j"  %% "slf4j-api"
    "org.slf4j"  %% "jul-to-slf4j"
    "org.slf4j"  %% "jcl-over-slf4j"
    
    "log4j"  %% "log4j"
    
    "org.slf4j"  %% "slf4j-log4j12"
    
    "com.ning"  %% "compress-lzf"
    
    "org.xerial.snappy"  %% "snappy-java"
    
    "org.lz4"  %% "lz4-java"
    
    "com.github.luben"  %% "zstd-jni"
    
    "org.roaringbitmap"  %% "RoaringBitmap"
    
    "commons-net"  %% "commons-net"
    
    "org.scala-lang"  %% "scala-library"
    
    "org.json4s"  %% "json4s-jackson_${scala.binary.version}"
    
    "org.glassfish.jersey.core"  %% "jersey-client"
    
    "org.glassfish.jersey.core"  %% "jersey-common"
    
    "org.glassfish.jersey.core"  %% "jersey-server"
    
    "org.glassfish.jersey.containers"  %% "jersey-container-servlet"
    
    "org.glassfish.jersey.containers"  %% "jersey-container-servlet-core"
    
    "io.netty"  %% "netty-all"
    
    "io.netty"  %% "netty"
    
    "com.clearspring.analytics"  %% "stream"
    
    "io.dropwizard.metrics"  %% "metrics-core"
    
    "io.dropwizard.metrics"  %% "metrics-jvm"
    
    "io.dropwizard.metrics"  %% "metrics-json"
    
    "io.dropwizard.metrics"  %% "metrics-graphite"
    
    "com.fasterxml.jackson.core"  %% "jackson-databind"
    
    "com.fasterxml.jackson.module"  %% "jackson-module-scala_${scala.binary.version}"
    
    "org.apache.derby"  %% "derby"
      <scope>test</scope>
    
    "org.apache.ivy"  %% "ivy"
    
    "oro"
      <!-- oro is needed by ivy, but only listed as an optional dependency, so we include it. -->  %% "oro"
      <version>${oro.version}</version>
    
    "org.seleniumhq.selenium"  %% "selenium-java"
      <scope>test</scope>
    
    "org.seleniumhq.selenium"  %% "selenium-htmlunit-driver"
      <scope>test</scope>
    
    <!-- Added for selenium: -->
    "xml-apis"  %% "xml-apis"
      <scope>test</scope>
    
    "org.hamcrest"  %% "hamcrest-core"
      <scope>test</scope>
    
    "org.hamcrest"  %% "hamcrest-library"
      <scope>test</scope>
    
    "org.mockito"  %% "mockito-core"
      <scope>test</scope>
    
    "org.scalacheck"  %% "scalacheck_${scala.binary.version}"
      <scope>test</scope>
    
    "org.apache.curator"  %% "curator-test"
      <scope>test</scope>
    
    "net.razorvine"  %% "pyrolite"
      <version>4.13</version>
      <exclusions>
        <exclusion>
          <groupId>net.razorvine"
     %% "serpent"
        </exclusion>
      </exclusions>
    
    "net.sf.py4j"  %% "py4j"
      <version>0.10.6</version>
    
    "org.apache.spark"  %% "spark-tags_${scala.binary.version}"
    

    "org.apache.spark"  %% "spark-launcher_${scala.binary.version}"
      <version>${project.version}</version>
      <classifier>tests</classifier>
      <scope>test</scope>
    

    <!--
      This spark-tags test-dep is needed even though it isn't used in this module, otherwise testing-cmds that exclude
      them will yield errors.
    -->
    "org.apache.spark"  %% "spark-tags_${scala.binary.version}"
      <type>test-jar</type>
      <scope>test</scope>
    

    "org.apache.commons"  %% "commons-crypto" 

    )
    