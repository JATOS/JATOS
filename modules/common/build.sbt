name := "jatos-common"
version := "3.10.5"
organization := "org.jatos"
scalaVersion := "2.13.18"
maintainer := "lange.kristian@gmail.com"

libraryDependencies ++= Seq(
  guice,
  javaWs,
  javaJpa,
  evolutions,
  jdbc,
  "org.hibernate.orm" % "hibernate-core" % "6.6.54.Final",
  "com.fasterxml.jackson.datatype" % "jackson-datatype-hibernate5-jakarta" % "2.14.3",
  "mysql" % "mysql-connector-java" % "8.0.33",
  "org.jsoup" % "jsoup" % "1.18.1",
  "commons-io" % "commons-io" % "2.15.1",
  "com.diffplug.durian" % "durian" % "3.4.0",
  "org.apache.commons" % "commons-lang3" % "3.18.0",
  "org.mockito" % "mockito-core" % "5.23.0" % Test,
  "org.assertj" % "assertj-core" % "3.27.7" % Test
)

// No source docs in distribution
sources in(Compile, doc) := Seq.empty

// No source docs in distribution 
publishArtifact in(Compile, packageDoc) := false

dockerBaseImage := "eclipse-temurin:8-jre"

MockitoSettings.settings
