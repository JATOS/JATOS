name := "jatos-common"
version := "3.3.5"
organization := "org.jatos"
scalaVersion := "2.11.11"

libraryDependencies ++= Seq(
  javaCore,
  javaJdbc,
  javaJpa,
  javaWs,
  cache,
  evolutions,
  "org.hibernate" % "hibernate-entitymanager" % "5.1.0.Final",
  "com.fasterxml.jackson.datatype" % "jackson-datatype-hibernate5" % "2.8.7",
  "mysql" % "mysql-connector-java" % "5.1.40",
  "org.jsoup" % "jsoup" % "1.10.2",
  "commons-io" % "commons-io" % "2.4",
  "com.diffplug.durian" % "durian" % "3.4.0"
)

// No source docs in distribution
sources in(Compile, doc) := Seq.empty

// No source docs in distribution 
publishArtifact in(Compile, packageDoc) := false
