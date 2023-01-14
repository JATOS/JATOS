name := "jatos-common"
version := "3.7.6"
organization := "org.jatos"
scalaVersion := "2.13.8"
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
  "org.hibernate" % "hibernate-core" % "5.4.24.Final",
  "com.fasterxml.jackson.datatype" % "jackson-datatype-hibernate5" % "2.9.8",
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.9.10.8",
  "mysql" % "mysql-connector-java" % "8.0.28",
  "org.jsoup" % "jsoup" % "1.14.2",
  "commons-io" % "commons-io" % "2.7",
  "com.diffplug.durian" % "durian" % "3.4.0",
  "org.apache.commons" % "commons-lang3" % "3.9"
)

// No source docs in distribution
sources in(Compile, doc) := Seq.empty

// No source docs in distribution 
publishArtifact in(Compile, packageDoc) := false
