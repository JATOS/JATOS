import com.typesafe.sbt.packager.docker._
import sbtbuildinfo.BuildInfoPlugin.autoImport.buildInfoKeys

name := "JATOS"
version := "3.3.6"
organization := "org.jatos"
scalaVersion := "2.11.12"

libraryDependencies ++= Seq(
  "org.mockito" % "mockito-core" % "2.26.0" % "test",
  "org.easytesting" % "fest-assert" % "1.4" % "test",
  "com.h2database" % "h2" % "1.4.193",
  "com.typesafe.play" %% "play-json" % "2.7.2",
  "org.apache.commons" % "commons-lang3" % "3.9",
  "org.hibernate" % "hibernate-core" % "5.4.0.Final",
  "mysql" % "mysql-connector-java" % "8.0.16",
  guice,
  filters
)

// Docker commands to run in Dockerfile
dockerCommands := Seq(
  Cmd("FROM", "openjdk:8-jre"),
  Cmd("MAINTAINER", "Kristian Lange"),
  Cmd("WORKDIR", "/opt/docker"),
  Cmd("ADD", "opt /opt"),
  Cmd("EXPOSE", "9000 9443"),
  Cmd("RUN", "apt update -y && apt install vim -y"),
  ExecCmd("RUN", "mkdir", "-p", "/opt/docker/logs"),
  ExecCmd("RUN", "chown", "-R", "daemon:daemon", "."),
  ExecCmd("RUN", "chmod", "u+x", "loader.sh"),
  Cmd("VOLUME", "/opt/docker/logs"),
  Cmd("RUN", "bash -l -c 'echo export JATOS_SECRET=$(LC_ALL=C tr -cd '[:alnum:]' < /dev/urandom | fold -w128 | head -n1) >> /etc/bash.bashrc'"),
  Cmd("USER", "daemon"),
  ExecCmd("ENTRYPOINT", "./loader.sh", "start")
)

javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint")

initialize := {
  val _ = initialize.value
  if (sys.props("java.specification.version") != "1.8")
    sys.error("Java 8 is required for this project.")
}

PlayKeys.externalizeResources := false

// JATOS root project with GUI. Container for all the submodules
lazy val jatos: Project = (project in file("."))
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
    .enablePlugins(PlayJava)
    .dependsOn(common)

// Routes from submodules
routesGenerator := InjectedRoutesGenerator

// No source docs in distribution 
sources in(Compile, doc) := Seq.empty

// No source docs in distribution 
publishArtifact in(Compile, packageDoc) := false

// Add loader.sh to distribution
mappings in Universal += file(baseDirectory.value + "/loader.sh") -> "loader.sh"

// Add loader.sh to distribution
mappings in Universal in packageBin += file(baseDirectory.value + "/loader.bat") -> "loader.bat"

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

