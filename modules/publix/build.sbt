import Common._

name := "jatos-publix"

libraryDependencies ++= Seq(
  guice,
  javaWs,
  javaJpa,
  "org.apache.commons" % "commons-collections4" % "4.5.0"
) ++ testDependencies