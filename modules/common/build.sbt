import SharedSettings._

name := "jatos-common"

libraryDependencies ++= Seq(
  guice,
  javaWs,
  javaJpa,
  evolutions,
  jdbc,
  "org.hibernate.orm" % "hibernate-core" % "6.6.54.Final",
  "com.fasterxml.jackson.datatype" % "jackson-datatype-hibernate6" % jacksonVersion,
  "com.mysql" % "mysql-connector-j" % "8.4.0",
  "org.jsoup" % "jsoup" % "1.23.2",
  "commons-io" % "commons-io" % "2.22.0",
  "com.diffplug.durian" % "durian" % "3.4.0",
  "com.github.spotbugs" % "spotbugs-annotations" % "4.10.3" % Provided,
  "org.apache.commons" % "commons-lang3" % "3.20.0"
) ++ testDependencies