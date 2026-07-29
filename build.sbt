import com.typesafe.sbt.packager.docker._
import sbtbuildinfo.BuildInfoPlugin.autoImport.buildInfoKeys
import Common._

name := "JATOS"
maintainer := "support@jatos.org"
Universal / packageName := "jatos"
Docker / packageName := "jatos/jatos"

// Docker commands to run in Dockerfile
dockerCommands := Seq(
  Cmd("FROM", "eclipse-temurin:25-jre-jammy"),
  Cmd("LABEL", "maintainer=support@jatos.org"),
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

javacOptions ++= Seq("--release", "25", "-Xlint")

PlayKeys.externalizeResources := false

// Submodule jatos-common: common utils for JSON, disk IO and such
lazy val common = (project in file("modules/common"))
  .enablePlugins(PlayJava, PlayScala, BuildInfoPlugin)
  .settings(commonSettings)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "general.common"
  )

// Submodule jatos-gui: responsible for GUI
lazy val gui = (project in file("modules/gui"))
  .enablePlugins(PlayJava, PlayScala, SbtWeb)
  .dependsOn(common)
  .settings(commonSettings)
  .settings(
    routesGenerator := InjectedRoutesGenerator
  )

// Submodule jatos-publix: responsible for running studies
lazy val publix = (project in file("modules/publix"))
  .enablePlugins(PlayJava, PlayScala)
  .dependsOn(common, session)
  .settings(commonSettings)
  .settings(
    routesGenerator := InjectedRoutesGenerator
  )


// Submodule jatos-session: does group and batch session
lazy val session = (project in file("modules/session"))
  .enablePlugins(PlayJava, PlayScala)
  .dependsOn(common)
  .settings(commonSettings)
  .settings(
    routesGenerator := InjectedRoutesGenerator
  )

// JATOS root project with GUI. Container for all the submodules
lazy val jatos = (project in file("."))
  .enablePlugins(PlayScala, SbtWeb)
  .aggregate(publix, session, common, gui)
  .dependsOn(publix, session, common, gui)
  .settings(commonSettings)
  .settings(
    aggregateReverseRoutes := Seq(publix, session, common, gui),
    Assets / pipelineStages += digest,
    routesGenerator := InjectedRoutesGenerator,

    libraryDependencies ++= Seq(
      guice,
      filters,
      "com.h2database" % "h2" % "2.4.240",
      "org.apache.commons" % "commons-lang3" % "3.20.0",
      "com.pivovarit" % "throwing-function" % "1.6.1"
    ) ++ testDependencies,

    // StudyAssets serves publix module assets under the dependency-style asset path
    // used by the running application: /public/lib/jatos-publix/...
    // The root integration test app does not naturally put publix's public assets
    // on its test classpath under that namespace, so mirror that layout for tests.
    Test / resourceGenerators += Def.task {
      val sourceDir = baseDirectory.value / "modules" / "publix" / "public"
      val targetDir = (Test / resourceManaged).value / "public" / "lib" / "jatos-publix"

      IO.delete(targetDir)
      IO.copyDirectory(sourceDir, targetDir)

      (targetDir ** "*").get.filter(_.isFile)
    }.taskValue,

    // Add files to distribution
    Universal / mappings ++= Seq(
      file(baseDirectory.value + "/loader.sh") -> "loader.sh",
      file(baseDirectory.value + "/loader.bat") -> "loader.bat",
      file(baseDirectory.value + "/VERSION") -> "VERSION",
      file(baseDirectory.value + "/conf/jatos.conf") -> "conf/jatos.conf"
    ),

    // Filter out unwanted files from distribution
    Universal / mappings := (Universal / mappings).value.filterNot {
      case (_, path) =>
        path.endsWith("development.conf") ||
          path.endsWith("testing.conf") ||
          path.endsWith("jatos.bat") ||
          path.contains("share/doc")
    }
  )