import com.typesafe.config._

name := "jatos-gui"

Common.settings

includeFilter in (Assets, LessKeys.less) := "*.less"

excludeFilter in (Assets, LessKeys.less) := "_*.less"

libraryDependencies ++= Seq(
	"org.webjars" % "bootstrap" % "3.3.4"
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
