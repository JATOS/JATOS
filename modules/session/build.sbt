name := "jatos-session"
version := "3.9.7"
organization := "org.jatos"
scalaVersion := "2.13.8"
maintainer := "lange.kristian@gmail.com"

libraryDependencies ++= Seq(
  guice,
  "org.apache.commons" % "commons-collections4" % "4.3",
  "org.gnieh" %% f"diffson-play-json" % "4.1.1"
)

// Routes from submodules
routesGenerator := InjectedRoutesGenerator

// No source docs in distribution 
sources in(Compile, doc) := Seq.empty

// No source docs in distribution 
publishArtifact in(Compile, packageDoc) := false
