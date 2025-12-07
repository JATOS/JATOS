import com.typesafe.sbt.packager.docker._
import sbtbuildinfo.BuildInfoPlugin.autoImport.buildInfoKeys

name := "JATOS"
version := "3.9.8"
organization := "org.jatos"
scalaVersion := "2.13.17"
maintainer := "lange.kristian@gmail.com"
packageName in Universal := "jatos"
packageName in Docker := "jatos/jatos"

libraryDependencies ++= Seq(
  guice,
  filters,
  "com.h2database" % "h2" % "1.4.197",
  "org.apache.commons" % "commons-lang3" % "3.18.0",
  "ch.qos.logback" % "logback-classic" % "1.5.13",
  "com.nimbusds" % "oauth2-oidc-sdk" % "11.23.1",
  "com.nimbusds" % "nimbus-jose-jwt" % "10.2",
  "com.pivovarit" % "throwing-function" % "1.6.1",
  "org.mockito" % "mockito-inline" % "4.11.0" % Test,
  "org.assertj" % "assertj-core" % "3.26.0" % Test
)

// Docker commands to run in Dockerfile
dockerCommands := Seq(
  Cmd("FROM", "eclipse-temurin:11-jre-jammy"),
  Cmd("LABEL", "maintainer=lange.kristian@gmail.com"),
  Cmd("ENV", "JATOS_HOME=/opt/jatos"),
  Cmd("ENV", "JATOS_DATA=/opt/jatos_data"),
  Cmd("WORKDIR", "${JATOS_HOME}"),
  Cmd("COPY", "opt/docker ${JATOS_HOME}"),
  Cmd("RUN", "groupadd --gid 1000 jatos " +
    "&& useradd --uid 1000 --gid 1000 jatos " +
    "&& mkdir -p ${JATOS_HOME}/logs ${JATOS_DATA} " +
    "&& chown -R jatos:jatos ${JATOS_HOME} ${JATOS_DATA}"),
  Cmd("USER", "jatos"),
  Cmd("EXPOSE", "9000"),
  Cmd("ENV", "JATOS_DB_URL=jdbc:h2:/opt/jatos_data/database/jatos;MODE=MYSQL;DATABASE_TO_UPPER=FALSE;IGNORECASE=TRUE;DEFAULT_LOCK_TIMEOUT=10000;SELECT_FOR_UPDATE_MVCC=FALSE"),
  Cmd("ENV", "JATOS_STUDY_ASSETS_ROOT_PATH=/opt/jatos_data/study_assets_root"),
  Cmd("ENV", "JATOS_RESULT_UPLOADS_PATH=/opt/jatos_data/result_uploads"),
  Cmd("ENV", "JATOS_STUDY_LOGS_PATH=/opt/jatos_data/study_logs"),
  Cmd("ENV", "JATOS_TMP_PATH=/opt/jatos_data/tmp"),
  ExecCmd("ENTRYPOINT", "./loader.sh", "start")
)

dockerBaseImage := "eclipse-temurin:8-jre"

javacOptions ++= Seq("--release", "11", "-Xlint")

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
    .enablePlugins(PlayJava, PlayScala)
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
mappings in Universal += file(baseDirectory.value + "/VERSION") -> "VERSION"

// Add conf/jatos.conf to distribution
mappings in Universal += file(baseDirectory.value + "/conf/jatos.conf") -> "conf/jatos.conf"

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

