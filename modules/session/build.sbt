import Common._

name := "jatos-session"

libraryDependencies ++= Seq(
  guice,
  "org.apache.commons" % "commons-collections4" % "4.3",
  "org.gnieh" %% "diffson-play-json" % "4.2.1"
) ++ testDependencies