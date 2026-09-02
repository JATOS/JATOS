import SharedSettings._

name := "jatos-session"

libraryDependencies ++= Seq(
  guice,
  "org.apache.commons" % "commons-collections4" % "4.5.0",
  "com.flipkart.zjsonpatch" % "zjsonpatch" % "0.4.16"
) ++ testDependencies