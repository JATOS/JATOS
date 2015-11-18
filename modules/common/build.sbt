import com.typesafe.config._

name := "jatos-common"

version := "2.1.1-beta"

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
	javaCore,
	javaJdbc,
	javaJpa,
	javaWs,
	evolutions,
	"org.hibernate" % "hibernate-entitymanager" % "4.3.11.Final",
	"mysql" % "mysql-connector-java" % "5.1.31",
	"org.jsoup" % "jsoup" % "1.8.1",
	"commons-io" % "commons-io" % "2.4",
	"org.mockito" % "mockito-core" % "1.9.5" % "test"
)

EclipseKeys.preTasks := Seq(compile in Compile)
EclipseKeys.projectFlavor := EclipseProjectFlavor.Java           // Java project. Don't expect Scala IDE
EclipseKeys.createSrc := EclipseCreateSrc.ValueSet(EclipseCreateSrc.ManagedClasses, EclipseCreateSrc.ManagedResources)  // Use .class files instead of generated .scala files for views and routes 
