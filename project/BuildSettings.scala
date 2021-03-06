import sbt.Keys._
import sbt._
import sbtassembly.AssemblyKeys._
import sbtassembly.MergeStrategy
import io.gatling.sbt.GatlingKeys.GatlingIt

object BuildSettings {
  val buildSettings: Seq[Setting[_]] = Defaults.coreDefaultSettings++ Seq(
    scalaVersion := "2.11.8",
    // TODO: enable style build failures
    com.socrata.sbtplugins.StylePlugin.StyleKeys.styleFailOnError in Compile := false,
    // TODO: enable findbugs build failures
    com.socrata.sbtplugins.findbugs.JavaFindBugsPlugin.JavaFindBugsKeys.findbugsFailOnError in Compile := false,
    com.socrata.sbtplugins.findbugs.JavaFindBugsPlugin.JavaFindBugsKeys.findbugsFailOnError in Test := false,
    fork in test := true,
    javaOptions in test += "-Dsocrata.env=test",
    scalaSource in GatlingIt := file("balboa-http/src/it/scala/com.socrata.balboa.server/load"),
    scalacOptions in (Compile, doc) ++= Seq( // Related Issue: http://scala-language.1934581.n4.nabble.com/Scaladoc-2-11-quot-throws-tag-quot-cannot-find-any-member-to-link-td4641850.html
      "-no-link-warnings" // Suppresses problems with Scaladoc @throws links
    ),
    scalacOptions ++= Seq("-unchecked", "-deprecation", "-language:implicitConversions", "-language:postfixOps"),
    publishTo := {
      val nexus = "https://repo.socrata.com/artifactory/"
      if (isSnapshot.value)
        Some("snapshots" at nexus + "libs-snapshot-local")
      else
        Some("releases"  at nexus + "libs-release-local")
    },
    // For the time being, leave this as targeting Java 7 bytecode so that
    // consumers of balboa libraries do not have to update their JVM to get the
    // newest balboa libraries.
    javacOptions ++= Seq("-source", "1.7", "-target", "1.7", "-Xlint"),
    initialize := {
      val _ = initialize.value
      if (sys.props("java.specification.version") != "1.8")
        sys.error("Java 8 is required for this project.")
    },
    assemblyMergeStrategy in assembly := {
      case "META-INF/io.netty.versions.properties" => MergeStrategy.last
      case x => (assemblyMergeStrategy in assembly).value(x)
    },
    resolvers ++= Seq(
      "socrata maven" at "https://repo.socrata.com/artifactory/libs-release/",
      Resolver.url("socrata ivy", new URL("https://repo.socrata.com/artifactory/ivy-libs-release"))(Resolver.ivyStylePatterns)
    )
  )
  val projectSettings: Seq[Setting[_]] = buildSettings
}
