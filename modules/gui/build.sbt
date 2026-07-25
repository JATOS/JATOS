name := "jatos-gui"
version := "3.10.5"
organization := "org.jatos"
scalaVersion := "2.13.18"
maintainer := "lange.kristian@gmail.com"

libraryDependencies ++= Seq(
  guice,
  javaWs,
  "com.google.api-client" % "google-api-client" % "1.34.0",
  "com.nimbusds" % "oauth2-oidc-sdk" % "11.23.1",
  "com.nimbusds" % "nimbus-jose-jwt" % "10.2",
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