name := "MechArg"

version := "1.0-SNAPSHOT"

libraryDependencies ++= Seq(
  javaCore,
  javaJdbc,
  javaJpa,
  "org.hibernate" % "hibernate-entitymanager" % "4.2.15.Final",
  "mysql" % "mysql-connector-java" % "5.1.18",
  "com.google.inject" % "guice" % "3.0",
  "org.jsoup" % "jsoup" % "1.8.1",
  "commons-io" % "commons-io" % "2.4"
)

playAssetsDirectories <+= baseDirectory / "studies"

play.Project.playJavaSettings

mappings in Universal += file(baseDirectory.value + "/start.sh") -> ("start.sh")
