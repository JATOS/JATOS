name := "jatos-publix"
version := "3.3.6"
organization := "org.jatos"
scalaVersion := "2.11.12"

libraryDependencies ++= Seq(
  javaCore,
  javaJdbc,
  javaJpa,
  javaWs,
  guice,
  "org.apache.commons" % "commons-collections4" % "4.3"
)

// Routes from submodules
routesGenerator := InjectedRoutesGenerator

// No source docs in distribution 
sources in(Compile, doc) := Seq.empty

// No source docs in distribution 
publishArtifact in(Compile, packageDoc) := false
