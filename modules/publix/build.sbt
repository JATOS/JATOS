import com.typesafe.config._

name := "jatos-publix"

Common.settings

libraryDependencies ++= Seq(
	"org.apache.commons" % "commons-collections4" % "4.0",
	"com.github.fge" % "json-patch" % "1.9"
)

// Compile the project before generating Eclipse files, so that .class files for views and routes are present
EclipseKeys.preTasks := Seq(compile in Compile)

// Java project. Don't expect Scala IDE
EclipseKeys.projectFlavor := EclipseProjectFlavor.Java

// Use .class files instead of generated .scala files for views and routes 
EclipseKeys.createSrc := EclipseCreateSrc.ValueSet(EclipseCreateSrc.ManagedClasses, EclipseCreateSrc.ManagedResources)

// Routes from submodules
routesGenerator := InjectedRoutesGenerator

// No source docs in distribution 
sources in (Compile, doc) := Seq.empty

// No source docs in distribution 
publishArtifact in (Compile, packageDoc) := false
