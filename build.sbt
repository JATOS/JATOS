import com.typesafe.sbt.packager.docker._
import sbtbuildinfo.BuildInfoPlugin.autoImport.buildInfoKeys

name := "JATOS"
version := "3.8.1-alpha"
organization := "org.jatos"
scalaVersion := "2.13.8"
maintainer := "lange.kristian@gmail.com"
packageName in Universal := "jatos"
packageName in Docker := "jatos/jatos"

libraryDependencies ++= Seq(
  "org.mockito" % "mockito-core" % "2.26.0" % "test",
  "org.easytesting" % "fest-assert" % "1.4" % "test",
  "com.h2database" % "h2" % "1.4.197",
  "com.typesafe.play" %% "play-json" % "2.8.2",
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.13.2.2",
  "org.apache.commons" % "commons-lang3" % "3.12.0",
  "ch.qos.logback" % "logback-classic" % "1.2.11",
  "ch.qos.logback" % "logback-core" % "1.2.11",
  guice,
  filters
)

// Docker commands to run in Dockerfile
dockerCommands := Seq(
  Cmd("FROM", "eclipse-temurin:11-jre-ubi9-minimal"),
  Cmd("MAINTAINER", "Kristian Lange"),
  Cmd("WORKDIR", "/opt/docker"),
  Cmd("ADD", "opt /opt"),
  Cmd("EXPOSE", "9000"),
  ExecCmd("RUN", "mkdir", "-p", "/opt/docker/logs"),
  ExecCmd("RUN", "chown", "-R", "daemon:daemon", "."),
  ExecCmd("RUN", "chmod", "u+x", "loader.sh"),
  Cmd("VOLUME", "/opt/docker/logs"),
  Cmd("USER", "daemon"),
  ExecCmd("ENTRYPOINT", "./loader.sh", "start")
)

javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint")

PlayKeys.externalizeResources := false

// JATOS root project with GUI. Container for all the submodules
lazy val jatos = (project in file("."))
    .enablePlugins(PlayScala, SbtWeb)
    .aggregate(publix, common, gui)
    .dependsOn(publix, common, gui)
    .settings(
      aggregateReverseRoutes := Seq(publix, common, gui)
    )

// Submodule jatos-utils: common utils for JSON, disk IO and such
lazy val common = (project in file("modules/common"))
    .enablePlugins(PlayJava, BuildInfoPlugin)
    .settings(
      buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
      buildInfoPackage := "general.common"
    )

// Submodule jatos-session: does group and batch session
lazy val session = (project in file("modules/session"))
    .enablePlugins(PlayScala)
    .dependsOn(common)

// Submodule jatos-publix: responsible for running studies
lazy val publix = (project in file("modules/publix"))
    .enablePlugins(PlayJava, PlayScala)
    .dependsOn(common, session)

// Submodule jatos-gui: responsible for running studies
lazy val gui = (project in file("modules/gui"))
    .enablePlugins(PlayJava, SbtWeb)
    .dependsOn(common)

// Routes from submodules
routesGenerator := InjectedRoutesGenerator

// No source docs in distribution 
sources in(Compile, doc) := Seq.empty

// No source docs in distribution 
publishArtifact in(Compile, packageDoc) := false

// Add loader.sh to distribution
mappings in Universal += file(baseDirectory.value + "/loader.sh") -> "loader.sh"

// Add loader.bat to distribution
mappings in Universal in packageBin += file(baseDirectory.value + "/loader.bat") -> "loader.bat"

// Add VERSION to distribution
mappings in Universal in packageBin += file(baseDirectory.value + "/VERSION") -> "VERSION"

// Add conf/production.conf to distribution
mappings in Universal += file(baseDirectory.value + "/conf/production.conf") -> "conf/production.conf"

// Don't include dev config to distribution
mappings in Universal := (mappings in Universal).value filter {
  case (file, path) => !path.endsWith("development.conf")
}

// Don't include test config to distribution
mappings in Universal := (mappings in Universal).value filter {
  case (file, path) => !path.endsWith("testing.conf")
}

// Don't include jatos.bat to distribution
mappings in Universal := (mappings in Universal).value filter {
  case (file, path) => !path.endsWith("jatos.bat")
}

// Don't include docs to distribution
mappings in Universal := (mappings in Universal).value filter {
  case (file, path) => !path.contains("share/doc")
}

Keys.fork in Test := false

