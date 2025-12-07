name := "jatos-gui"
version := "3.9.8"
organization := "org.jatos"
scalaVersion := "2.13.17"
maintainer := "lange.kristian@gmail.com"

libraryDependencies ++= Seq(
  guice,
  javaWs,
  javaJpa,
  "com.google.api-client" % "google-api-client" % "1.34.0",
  "com.nimbusds" % "oauth2-oidc-sdk" % "11.23.1",
  "com.nimbusds" % "nimbus-jose-jwt" % "10.2",
  "org.mockito" % "mockito-inline" % "4.11.0" % Test,
  "org.easytesting" % "fest-assert" % "1.4" % Test
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
