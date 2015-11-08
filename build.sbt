import com.typesafe.config._

import com.typesafe.sbteclipse.core.EclipsePlugin.EclipseKeys

version := "2.1.1-beta"

name := "JATOS"

organization := "org.jatos"

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
	javaCore,
	javaJdbc,
	javaJpa,
	javaWs,
	"com.google.inject" % "guice" % "4.0",
	"org.webjars" %% "webjars-play" % "2.3.0-2"
)

javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint")

initialize := {
	val _ = initialize.value
	if (sys.props("java.specification.version") != "1.8")
		sys.error("Java 8 is required for this project.")
}

EclipseKeys.skipParents in ThisBuild := false

// JATOS root project with GUI. Container for all the submodules
lazy val jatos = (project in file("."))
	.enablePlugins(PlayJava, SbtWeb)
	.aggregate(publix, common, daos, gui)
	.dependsOn(publix, common, daos, gui)

// Submodule jatos-utils: common utils for JSON, disk IO and such 
lazy val common = (project in file("modules/common"))
	.enablePlugins(PlayJava)

// Submodule jatos-persistance: models (JPA and JSON) and DAOs
lazy val daos = (project in file("modules/daos"))
	.enablePlugins(PlayJava).dependsOn(common)

// Submodule jatos-publix: responsible for running studies 
lazy val publix = (project in file("modules/publix"))
	.enablePlugins(PlayJava).dependsOn(common, daos)

// Submodule jatos-gui: responsible for running studies 
lazy val gui = (project in file("modules/gui"))
	.enablePlugins(PlayJava).dependsOn(common, daos)

mappings in Universal += file(baseDirectory.value + "/loader.sh") -> ("loader.sh")

mappings in Universal += file(baseDirectory.value + "/loader.bat") -> ("loader.bat")

mappings in Universal := (mappings in Universal).value filter { 
	case (file, path) => ! path.endsWith(".development.conf")
}

mappings in Universal := (mappings in Universal).value filter { 
	case (file, path) => ! path.endsWith(".testing.conf")
}

mappings in Universal := (mappings in Universal).value filter { 
	case (file, path) => ! path.endsWith("jatos.bat")
}

mappings in Universal := (mappings in Universal).value filter { 
	case (file, path) => ! path.contains("share/doc")
}

Keys.fork in Test := false

