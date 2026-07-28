import Common._

name := "jatos-publix"

libraryDependencies ++= Seq(
  guice,
  javaWs,
  javaJpa,
  "org.apache.commons" % "commons-collections4" % "4.3"
) ++ testDependencies