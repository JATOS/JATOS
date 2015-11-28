import com.typesafe.config._

name := "jatos-common"

version := "2.1.1-beta"

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
	javaCore,
	javaJdbc,
	javaJpa,
	javaWs,
	evolutions,
	"org.hibernate" % "hibernate-entitymanager" % "4.3.11.Final",
	"mysql" % "mysql-connector-java" % "5.1.31",
	"org.jsoup" % "jsoup" % "1.8.1",
	"commons-io" % "commons-io" % "2.4"
)

// Compile the project before generating Eclipse files, so that .class files for views and routes are present
EclipseKeys.preTasks := Seq(compile in Compile)

// Java project. Don't expect Scala IDE
EclipseKeys.projectFlavor := EclipseProjectFlavor.Java

// Use .class files instead of generated .scala files for views and routes 
EclipseKeys.createSrc := EclipseCreateSrc.ValueSet(EclipseCreateSrc.ManagedClasses, EclipseCreateSrc.ManagedResources)

// No source docs in distribution 
sources in (Compile, doc) := Seq.empty

// No source docs in distribution 
publishArtifact in (Compile, packageDoc) := false