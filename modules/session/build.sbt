import SharedSettings._

name := "jatos-session"

libraryDependencies ++= Seq(
  guice,
  "org.apache.commons" % "commons-collections4" % "4.5.0",
  "org.gnieh" %% "diffson-play-json" % "4.6.1"
) ++ testDependencies