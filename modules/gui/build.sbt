import com.typesafe.config._

name := "jatos-gui"

version := "2.1.1-beta"

scalaVersion := "2.11.6"

includeFilter in (Assets, LessKeys.less) := "*.less"

excludeFilter in (Assets, LessKeys.less) := "_*.less"

libraryDependencies ++= Seq(
	"org.webjars" % "bootstrap" % "3.3.4",
	"org.webjars" % "jquery" % "1.11.1"
)

EclipseKeys.preTasks := Seq(compile in Compile)
EclipseKeys.projectFlavor := EclipseProjectFlavor.Java           // Java project. Don't expect Scala IDE
EclipseKeys.createSrc := EclipseCreateSrc.ValueSet(EclipseCreateSrc.ManagedClasses, EclipseCreateSrc.ManagedResources)  // Use .class files instead of generated .scala files for views and routes 

routesGenerator := InjectedRoutesGenerator

Keys.fork in Test := false
