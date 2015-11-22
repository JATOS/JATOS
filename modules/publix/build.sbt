import com.typesafe.config._

name := "jatos-publix"

version := "2.1.1-beta"

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
)

EclipseKeys.preTasks := Seq(compile in Compile)
EclipseKeys.projectFlavor := EclipseProjectFlavor.Java           // Java project. Don't expect Scala IDE
EclipseKeys.createSrc := EclipseCreateSrc.ValueSet(EclipseCreateSrc.ManagedClasses, EclipseCreateSrc.ManagedResources)  // Use .class files instead of generated .scala files for views and routes 

routesGenerator := InjectedRoutesGenerator
