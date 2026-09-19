name := "jatos-session"
version := "3.11.3"
organization := "org.jatos"
scalaVersion := "2.13.8"
maintainer := "lange.kristian@gmail.com"

libraryDependencies ++= Seq(
  guice,
  "org.apache.commons" % "commons-collections4" % "4.3",
  "com.flipkart.zjsonpatch" % "zjsonpatch" % "0.4.16",
  "org.mockito" % "mockito-inline" % "4.11.0" % Test
)

// Routes from submodules
routesGenerator := InjectedRoutesGenerator

// No source docs in distribution 
sources in(Compile, doc) := Seq.empty

// No source docs in distribution 
publishArtifact in(Compile, packageDoc) := false

dockerBaseImage := "eclipse-temurin:8-jre"
