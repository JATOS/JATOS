import Common._

name := "jatos-gui"

libraryDependencies ++= Seq(
  guice,
  javaWs,
  "com.google.api-client" % "google-api-client" % "2.9.0",
  "com.nimbusds" % "oauth2-oidc-sdk" % "11.38.2",
  "com.nimbusds" % "nimbus-jose-jwt" % "10.9.1"
) ++ testDependencies