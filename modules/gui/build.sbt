name := "jatos-gui"
version := "3.9.8"
organization := "org.jatos"
scalaVersion := "2.13.8"
maintainer := "lange.kristian@gmail.com"

libraryDependencies ++= Seq(
  javaCore,
  javaJdbc,
  javaJpa,
  javaWs,
  guice,
  "com.typesafe.play" %% "play-json" % "2.8.2",
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.13.2.2",
  "com.google.api-client" % "google-api-client" % "1.34.0",
  "com.nimbusds" % "oauth2-oidc-sdk" % "10.4",
  "com.nimbusds" % "nimbus-jose-jwt" % "9.27",
  "org.mockito" % "mockito-inline" % "4.11.0" % "test",
  "org.easytesting" % "fest-assert" % "1.4" % "test"
)

// Routes from submodules
routesGenerator := InjectedRoutesGenerator

// No source docs in distribution 
sources in(Compile, doc) := Seq.empty

// No source docs in distribution 
publishArtifact in(Compile, packageDoc) := false

// Add versioning with Etag to assets (e.g. for CSS files)
pipelineStages := Seq(digest)

dockerBaseImage := "eclipse-temurin:8-jre"
