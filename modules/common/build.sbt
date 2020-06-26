name := "jatos-common"
version := "3.5.6-alpha"
organization := "org.jatos"
scalaVersion := "2.11.12"
maintainer := "lange.kristian@gmail.com"

libraryDependencies ++= Seq(
  javaCore,
  javaJdbc,
  javaJpa,
  javaWs,
  ehcache,
  evolutions,
  jdbc,
  guice,
  "org.hibernate" % "hibernate-core" % "5.4.2.Final",
  "com.fasterxml.jackson.datatype" % "jackson-datatype-hibernate5" % "2.9.8",
  "mysql" % "mysql-connector-java" % "8.0.16",
  "org.jsoup" % "jsoup" % "1.11.3",
  "commons-io" % "commons-io" % "2.6",
  "com.diffplug.durian" % "durian" % "3.4.0",
  "org.apache.commons" % "commons-lang3" % "3.9"
)

// No source docs in distribution
sources in(Compile, doc) := Seq.empty

// No source docs in distribution 
publishArtifact in(Compile, packageDoc) := false
