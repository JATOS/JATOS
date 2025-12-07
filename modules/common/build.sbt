name := "jatos-common"
version := "3.9.8"
organization := "org.jatos"
scalaVersion := "2.13.17"
maintainer := "lange.kristian@gmail.com"

libraryDependencies ++= Seq(
  guice,
  javaWs,
  javaJpa,
  evolutions,
  jdbc,
  "org.hibernate" % "hibernate-core" % "5.6.15.Final",
  "com.fasterxml.jackson.datatype" % "jackson-datatype-hibernate5" % "2.11.4",
  "mysql" % "mysql-connector-java" % "8.0.33",
  "org.jsoup" % "jsoup" % "1.18.1",
  "commons-io" % "commons-io" % "2.15.1",
  "com.diffplug.durian" % "durian" % "3.4.0",
  "org.apache.commons" % "commons-lang3" % "3.18.0",
  "org.mockito" % "mockito-inline" % "4.11.0" % "test",
  "org.easytesting" % "fest-assert" % "1.4" % "test"
)

// No source docs in distribution
sources in(Compile, doc) := Seq.empty

// No source docs in distribution 
publishArtifact in(Compile, packageDoc) := false

dockerBaseImage := "eclipse-temurin:8-jre"
