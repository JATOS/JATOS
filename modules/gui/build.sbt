import Common._

name := "jatos-gui"

libraryDependencies ++= Seq(
  guice,
  javaWs,
  "com.google.api-client" % "google-api-client" % "1.34.0",
  "com.nimbusds" % "oauth2-oidc-sdk" % "11.23.1",
  "com.nimbusds" % "nimbus-jose-jwt" % "10.2"
) ++ testDependencies