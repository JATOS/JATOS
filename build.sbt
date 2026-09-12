import sbtbuildinfo.BuildInfoPlugin.autoImport.buildInfoKeys
import SharedSettings._

name := "JATOS"
maintainer := "support@jatos.org"
Universal / packageName := "jatos"
Docker / packageName := "jatos/jatos"

// Submodule jatos-common: common utils for JSON, disk IO and such
lazy val common = (project in file("modules/common"))
  .enablePlugins(PlayJava, PlayScala, BuildInfoPlugin)
  .settings(sharedSettings)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "general.common"
  )

// Submodule jatos-gui: responsible for GUI
lazy val gui = (project in file("modules/gui"))
  .enablePlugins(PlayJava, PlayScala, SbtWeb)
  .dependsOn(common)
  .settings(sharedSettings)
  .settings(
    routesGenerator := InjectedRoutesGenerator
  )

// Submodule jatos-publix: responsible for running studies
lazy val publix = (project in file("modules/publix"))
  .enablePlugins(PlayJava, PlayScala)
  .dependsOn(common, session)
  .settings(sharedSettings)
  .settings(
    routesGenerator := InjectedRoutesGenerator
  )


// Submodule jatos-session: does group and batch session
lazy val session = (project in file("modules/session"))
  .enablePlugins(PlayJava, PlayScala)
  .dependsOn(common)
  .settings(sharedSettings)
  .settings(
    routesGenerator := InjectedRoutesGenerator
  )

// JATOS root project with GUI. Container for all the submodules
lazy val jatos = (project in file("."))
  .enablePlugins(PlayScala, SbtWeb)
  .aggregate(publix, session, common, gui)
  .dependsOn(publix, session, common, gui)
  .settings(sharedSettings)
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