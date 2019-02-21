name := "jatos-publix"
version := "3.3.5"
organization := "org.jatos"
scalaVersion := "2.11.11"

libraryDependencies ++= Seq(
  javaCore,
  javaJdbc,
  javaJpa,
  javaWs,
  "org.apache.commons" % "commons-collections4" % "4.0"
)

// Routes from submodules
routesGenerator := InjectedRoutesGenerator

// No source docs in distribution 
sources in(Compile, doc) := Seq.empty

// No source docs in distribution 
publishArtifact in(Compile, packageDoc) := false
