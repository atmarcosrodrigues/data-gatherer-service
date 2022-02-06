import com.typesafe.sbt.SbtNativePackager.autoImport._
import DebianConstants._

name := "service-broker"
organization := "com.silibrina.tecnova"
version := "0.0.3"
description := "Service for data gathering, proxying and caching"

maintainer in Linux := "Marcos R <@.com>"

packageSummary in Linux := "Service-broker for data gathering, proxying and caching"

daemonUser in Linux := "opendata"
daemonGroup in Linux := "opendata"

val openDataDir = "/var/lib/opendata"

maintainerScripts in Debian := maintainerScriptsAppend((maintainerScripts in Debian).value)(
  Preinst -> "echo 'Here we go...'",
	Postinst ->
    s"""/bin/mkdir -p $openDataDir
     |/bin/chown ${(daemonUser in Linux).value}:${(daemonGroup in Linux).value} -R $openDataDir
     |/bin/chmod 744 -R $openDataDir
     |/bin/mkdir -p /usr/share/service-broker/modules""".stripMargin
)

packageDescription := "This service provides a simple way to make data available and easily providing them through an " +
  "extensible API. It is plugin oriented, developed to be a data aggregator as much as a broker between the frontend and " +
  "other data providers. It offer an API complaint with Cloud Foundry, so it can be deployed as a service broker in this PaaS."

lazy val broker = (project in file("."))
  .enablePlugins(PlayJava, DebianPlugin, SystemdPlugin)
  .dependsOn(commons % "test->test;compile->compile", coherence % "test->test;compile->compile", converter % "test->test;compile->compile")
  .aggregate(commons, coherence, converter)

lazy val commons = uri("ssh://git@bitbucket.org/silibrina/service-broker-commons.git")
lazy val coherence = uri("ssh://git@bitbucket.org/silibrina/coherence-service.git")
lazy val converter = uri("ssh://git@bitbucket.org/silibrina/conversion-service.git")

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  javaJdbc,
  cache,
  javaWs
)

// Play provides two styles of routers, one expects its actions to be injected, the
// other, legacy style, accesses its actions statically.
routesGenerator := InjectedRoutesGenerator

// https://mvnrepository.com/artifact/org.apache.commons/commons-io
libraryDependencies += "org.apache.commons" % "commons-io" % "1.3.2"

// https://mvnrepository.com/artifact/org.apache.poi/poi
libraryDependencies += "org.apache.poi" % "poi" % "3.14"

// https://mvnrepository.com/artifact/org.apache.poi/poi-ooxml
libraryDependencies += "org.apache.poi" % "poi-ooxml" % "3.14"

// http://mvnrepository.com/artifact/org.jongo/jongo
libraryDependencies += "org.jongo" % "jongo" % "1.3.0"

// http://mvnrepository.com/artifact/org.mongodb/mongo-java-driver
libraryDependencies += "org.mongodb" % "mongo-java-driver" % "3.2.2"

// http://mvnrepository.com/artifact/junit/junit
libraryDependencies += "junit" % "junit" % "4.12" % "test"

// https://mvnrepository.com/artifact/org.apache.tika/tika-core
libraryDependencies += "org.apache.tika" % "tika-core" % "1.13"

// https://mvnrepository.com/artifact/org.apache.tika/tika-parsers
libraryDependencies += "org.apache.tika" % "tika-parsers" % "1.13"

// https://mvnrepository.com/artifact/org.apache.lucene/lucene-core
libraryDependencies += "org.apache.lucene" % "lucene-core" % "6.1.0"

// https://mvnrepository.com/artifact/org.apache.lucene/lucene-analyzers-common
libraryDependencies += "org.apache.lucene" % "lucene-analyzers-common" % "6.1.0"

// https://mvnrepository.com/artifact/org.apache.lucene/lucene-queryparser
libraryDependencies += "org.apache.lucene" % "lucene-queryparser" % "6.1.0"

fork in Test := true // allow to apply extra setting to Test

debianPackageDependencies in Debian ++= Seq("oracle-java8-jdk (>= 8u102)")
debianPackageRecommends in Debian ++= Seq("rabbitmq-server (>= 3.3.5-1.1)", "mongodb-server (>= 1:2.4.10-5)")
