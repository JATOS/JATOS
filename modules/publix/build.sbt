name := "jatos-publix"
version := "3.10.5"
organization := "org.jatos"
scalaVersion := "2.13.18"
maintainer := "lange.kristian@gmail.com"

libraryDependencies ++= Seq(
  guice,
  javaWs,
  javaJpa,
  "org.apache.commons" % "commons-collections4" % "4.3",
  "org.mockito" % "mockito-core" % "5.23.0" % Test,
  "org.assertj" % "assertj-core" % "3.27.7" % Test
)

// Routes from submodules
routesGenerator := InjectedRoutesGenerator

// No source docs in distribution 
sources in(Compile, doc) := Seq.empty

// No source docs in distribution 
publishArtifact in(Compile, packageDoc) := false

dockerBaseImage := "eclipse-temurin:8-jre"

MockitoSettings.settings