import com.typesafe.sbt.packager.Keys.maintainer
import sbt.*
import sbt.Keys.*

object SharedSettings {
  val jatosMaintainer = "support@jatos.org"
  val jatosOrganization = "org.jatos"
  val jatosJavaRelease = "21"
  val jatosScalaVersion = "2.13.18"

  val jatosVersion: String = {
    val versionFile = file("VERSION")
    val version = IO.read(versionFile).trim

    if (version.isEmpty) {
      sys.error("VERSION must not be empty")
    }

    version
  }

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

      Seq(
        s"-javaagent:${mockitoJar.getAbsolutePath}",
        "-Dconfig.resource=testing.conf"
      )
    }
  )

  // Common settings for all modules
  val sharedSettings: Seq[Def.Setting[?]] = Seq(
    maintainer := jatosMaintainer,
    version := jatosVersion,
    organization := jatosOrganization,
    scalaVersion := jatosScalaVersion,
    javacOptions ++= Seq("--release", jatosJavaRelease, "-Xlint"),
    Compile / doc / sources := Seq.empty,
    Compile / packageDoc / publishArtifact := false,
  ) ++ mockitoSettings
}