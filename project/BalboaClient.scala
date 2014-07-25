import sbt._
import Keys._

import com.socrata.cloudbeessbt.SocrataCloudbeesSbt._
import SocrataSbtKeys._

object BalboaClient {
  lazy val settings: Seq[Setting[_]] = BuildSettings.projectSettings() ++ Seq(
    crossScalaVersions := Seq("2.8.2", "2.10.1"),
    libraryDependencies ++= Seq(
      "junit" % "junit" % "4.5" % "test",
      "org.scalatest" %% "scalatest" % "[1.8, 1.9.1)" % "test",
      "org.apache.activemq" % "activemq-core" % "5.2.0",
      "com.socrata" %% "socrata-utils" % "[0.6.0,1.0.0)",
      "com.rojoma" %% "simple-arm" % "[1.1.10, 2.0.0)",
      "log4j" % "log4j" % "1.2.16"
    ),
    dependenciesSnippet :=
      <xml.group>
        <conflict org="com.socrata" manager="latest-compatible" />
        <conflict org="com.rojoma" manager="latest-compatible" />
      </xml.group>
  )
}

