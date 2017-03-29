import com.typesafe.config._

import com.typesafe.sbteclipse.core.EclipsePlugin.EclipseKeys

import com.typesafe.sbt.packager.docker._

// Settings that are common to all modules are in project/Common.scala

name := "JATOS"

Common.settings

libraryDependencies ++= Seq(
	"org.mockito" % "mockito-core" % "1.9.5" % "test",
	"org.easytesting" % "fest-assert" % "1.4" % Test,
	"com.h2database" % "h2" % "1.4.192",
	javaCore,
	javaJdbc,
	javaJpa,
	javaWs,
	filters
)

// Docker commands to run in Dockerfile
dockerCommands := Seq(
	Cmd("FROM", "java:8-jre"),
	Cmd("MAINTAINER", "Kristian Lange"),
	Cmd("WORKDIR", "/opt/docker"),
	Cmd("ADD", "opt /opt"),
	Cmd("EXPOSE", "9000 9443"),
	Cmd("RUN", "apt-get update -y && apt-get install vim -y"),
	ExecCmd("RUN", "mkdir", "-p", "/opt/docker/logs"),
	ExecCmd("RUN", "chown", "-R", "daemon:daemon", "."),
	Cmd("VOLUME", "/opt/docker/logs"),
	Cmd("RUN", "bash -l -c 'echo export JATOS_SECRET=$(LC_ALL=C tr -cd '[:alnum:]' < /dev/urandom | fold -w128 | head -n1) >> /etc/bash.bashrc'"),
	Cmd("USER", "daemon"),
	ExecCmd("ENTRYPOINT", "bin/jatos", "-Dconfig.file=conf/production.conf", "-J-server")
)

javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint")	

initialize := {
	val _ = initialize.value
	if (sys.props("java.specification.version") != "1.8")
		sys.error("Java 8 is required for this project.")
}

EclipseKeys.skipParents in ThisBuild := false

// Compile the project before generating Eclipse files, so that .class files for views and routes are present
EclipseKeys.preTasks := Seq(compile in Compile)

// Java project. Don't expect Scala IDE
EclipseKeys.projectFlavor := EclipseProjectFlavor.Java

// Use .class files instead of generated .scala files for views and routes 
EclipseKeys.createSrc := EclipseCreateSrc.ValueSet(EclipseCreateSrc.ManagedClasses, EclipseCreateSrc.ManagedResources)

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

// Routes from submodules
routesGenerator := InjectedRoutesGenerator

// No source docs in distribution 
sources in (Compile, doc) := Seq.empty

// No source docs in distribution 
publishArtifact in (Compile, packageDoc) := false

// Add loader.sh to distribution
mappings in Universal in packageBin += file(baseDirectory.value + "/loader.sh") -> ("loader.sh")

// Add loader.sh to distribution
mappings in Universal in packageBin += file(baseDirectory.value + "/loader.bat") -> ("loader.bat")

// Add conf/production.conf to distribution
mappings in Universal += file(baseDirectory.value + "/conf/production.conf") -> ("conf/production.conf")

// Don't include dev config to distribution
mappings in Universal := (mappings in Universal).value filter {
	case (file, path) => ! path.endsWith("development.conf")
}

// Don't include test config to distribution
mappings in Universal := (mappings in Universal).value filter {
	case (file, path) => ! path.endsWith("testing.conf")
}

// Don't include jatos.bat to distribution
mappings in Universal := (mappings in Universal).value filter {
	case (file, path) => ! path.endsWith("jatos.bat")
}

// Don't include Java docs to distribution
mappings in Universal := (mappings in Universal).value filter { 
	case (file, path) => ! path.contains("share/doc")
}

Keys.fork in Test := false

