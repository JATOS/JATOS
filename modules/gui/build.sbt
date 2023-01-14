name := "jatos-gui"
version := "3.7.6"
organization := "org.jatos"
scalaVersion := "2.13.8"
maintainer := "lange.kristian@gmail.com"

includeFilter in(Assets, LessKeys.less) := "*.less"

excludeFilter in(Assets, LessKeys.less) := "_*.less"

libraryDependencies ++= Seq(
  javaCore,
  javaJdbc,
  javaJpa,
  javaWs,
  ehcache,
  guice,
  "org.webjars" % "bootstrap" % "3.4.1",
  "com.google.api-client" % "google-api-client" % "1.34.0"
)

// Routes from submodules
routesGenerator := InjectedRoutesGenerator

// No source docs in distribution 
sources in(Compile, doc) := Seq.empty

// No source docs in distribution 
publishArtifact in(Compile, packageDoc) := false

// Add versioning with Etag to assets (e.g. for CSS files)
pipelineStages := Seq(digest)
