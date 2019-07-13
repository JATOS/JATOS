name := "jatos-common"
version := "3.3.6"
organization := "org.jatos"
scalaVersion := "2.11.12"

libraryDependencies ++= Seq(
  javaCore,
  javaJdbc,
  javaJpa,
  javaWs,
  ehcache,
  evolutions,
  guice,
  "org.hibernate" % "hibernate-core" % "5.4.0.Final",
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
