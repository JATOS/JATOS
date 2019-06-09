name := "jatos-session"
version := "3.3.6"
organization := "org.jatos"
scalaVersion := "2.11.12"

libraryDependencies ++= Seq(
  guice,
  "org.apache.commons" % "commons-collections4" % "4.3",
  "org.gnieh" % "diffson-play-json_2.11" % "3.1.1"
)

// Routes from submodules
routesGenerator := InjectedRoutesGenerator

// No source docs in distribution 
sources in(Compile, doc) := Seq.empty

// No source docs in distribution 
publishArtifact in(Compile, packageDoc) := false
