import com.typesafe.config._

import com.typesafe.sbteclipse.core.EclipsePlugin.EclipseKeys

version := "2.1.1-beta"

name := "JATOS"

organization := "org.jatos"

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
	"org.mockito" % "mockito-core" % "1.9.5" % "test",
	"org.easytesting" % "fest-assert" % "1.4" % Test,
	javaCore,
	javaJdbc,
	javaJpa,
	javaWs
)

javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint")

initialize := {
	val _ = initialize.value
	if (sys.props("java.specification.version") != "1.8")
		sys.error("Java 8 is required for this project.")
}

EclipseKeys.projectFlavor := EclipseProjectFlavor.Java           // Java project. Don't expect Scala IDE
EclipseKeys.createSrc := EclipseCreateSrc.ValueSet(EclipseCreateSrc.ManagedClasses, EclipseCreateSrc.ManagedResources)  // Use .class files instead of generated .scala files for views and routes 
EclipseKeys.preTasks := Seq(compile in Compile)                  // Compile the project before generating Eclipse files, so that .class files for views and routes are present
EclipseKeys.skipParents in ThisBuild := false

PlayKeys.externalizeResources := false
	
// JATOS root project with GUI. Container for all the submodules
lazy val jatos: Project = (project in file("."))
	.enablePlugins(PlayJava, SbtWeb)
	.aggregate(publix, common, gui)
	.dependsOn(publix, common, gui)
	.settings(
		aggregateReverseRoutes := Seq(publix, common, gui)
	)

// Submodule jatos-utils: common utils for JSON, disk IO and such 
lazy val common = (project in file("modules/common"))
	.enablePlugins(PlayJava)

// Submodule jatos-publix: responsible for running studies 
lazy val publix = (project in file("modules/publix"))
	.enablePlugins(PlayJava).dependsOn(common)

// Submodule jatos-gui: responsible for running studies 
lazy val gui = (project in file("modules/gui"))
	.enablePlugins(PlayJava).dependsOn(common)

routesGenerator := InjectedRoutesGenerator

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

