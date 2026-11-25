import SharedSettings._

name := "jatos-gui"

libraryDependencies ++= Seq(
  guice,
  javaWs,
  "com.google.api-client" % "google-api-client" % "2.9.0",
  "com.nimbusds" % "oauth2-oidc-sdk" % "11.38.2",
  "com.nimbusds" % "nimbus-jose-jwt" % "10.9.1",
  "org.eclipse.jgit" % "org.eclipse.jgit" % "7.8.1.202611041320-r",
  "com.password4j" % "password4j" % "1.8.4"
) ++ testDependencies