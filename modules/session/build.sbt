name := "jatos-session"

scalaVersion := "2.11.11"

libraryDependencies ++= Seq(
  "org.apache.commons" % "commons-collections4" % "4.0",
  "org.gnieh" % "diffson-play-json_2.11" % "2.2.1"
)

// Routes from submodules
routesGenerator := InjectedRoutesGenerator

// No source docs in distribution 
sources in(Compile, doc) := Seq.empty

// No source docs in distribution 
publishArtifact in(Compile, packageDoc) := false
