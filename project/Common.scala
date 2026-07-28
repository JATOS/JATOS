import com.typesafe.sbt.packager.Keys.{dockerBaseImage, maintainer}
import sbt.*
import sbt.Keys.*

object Common {
  val jatosVersion = "3.10.5"
  val jatosOrganization = "org.jatos"
  val jatosScalaVersion = "2.13.18"

  // Dependency versions
  val mockitoVersion = "5.23.0"
  val assertjVersion = "3.27.7"

  // Test dependencies (mockito is handled separately via mockitoSettings)
  val testDependencies: Seq[ModuleID] = Seq(
    "org.assertj" % "assertj-core" % assertjVersion % Test
  )

  // Mockito settings with java agent for static mocking
  val mockitoSettings: Seq[Def.Setting[?]] = Seq(
    libraryDependencies += "org.mockito" % "mockito-core" % mockitoVersion % Test,

    Test / fork := true,

    Test / javaOptions ++= {
      val mockitoJar = (Test / dependencyClasspath).value
        .map(_.data)
        .find(_.getName.startsWith("mockito-core-"))
        .getOrElse(sys.error("mockito-core JAR not found on test classpath"))

      Seq(s"-javaagent:${mockitoJar.getAbsolutePath}")
    }
  )

  // Common settings for all modules
  val commonSettings: Seq[Def.Setting[?]] = Seq(
    version := jatosVersion,
    organization := jatosOrganization,
    scalaVersion := jatosScalaVersion,
    Compile / doc / sources := Seq.empty,
    Compile / packageDoc / publishArtifact := false,
  ) ++ mockitoSettings
}