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
  val guavaVersion = "33.4.8-jre"
  val pekkoVersion = "1.0.3"
  val jacksonVersion = "2.18.11"
  val lz4Version = "1.11.4"

  // Play/Pekko still request Jackson 2.14.3. Keep core and all modules on the patched
  // 2.18 line together; the Scala module checks the databind minor version at runtime.
  val jacksonOverrides: Seq[ModuleID] = Seq(
    "com.fasterxml.jackson.core" % "jackson-core",
    "com.fasterxml.jackson.core" % "jackson-annotations",
    "com.fasterxml.jackson.core" % "jackson-databind",
    "com.fasterxml.jackson.dataformat" % "jackson-dataformat-cbor",
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8",
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310",
    "com.fasterxml.jackson.datatype" % "jackson-datatype-hibernate6",
    "com.fasterxml.jackson.module" % "jackson-module-parameter-names",
    "com.fasterxml.jackson.module" %% "jackson-module-scala"
  ).map(_ % jacksonVersion)

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
    dependencyOverrides += "com.google.guava" % "guava" % guavaVersion,
    dependencyOverrides ++= jacksonOverrides,
    // Pekko's legacy org.lz4 artifact and Play's maintained fork contain the same classes.
    // Exclude the legacy coordinates everywhere; version overrides alone cannot replace a group ID.
    excludeDependencies += ExclusionRule("org.lz4", "lz4-java"),
    dependencyOverrides += "at.yawk.lz4" % "lz4-java" % lz4Version,
    javacOptions ++= Seq("--release", jatosJavaRelease, "-Xlint"),
    Compile / doc / sources := Seq.empty,
    Compile / packageDoc / publishArtifact := false,
  ) ++ mockitoSettings
}
