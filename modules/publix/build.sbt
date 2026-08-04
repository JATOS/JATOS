import SharedSettings._

name := "jatos-publix"

// Update the jatos.js file with the current version
Compile / resourceGenerators += Def.task {
  val source = baseDirectory.value / "public" / "javascripts" / "jatos.js"
  val target = (Compile / resourceManaged).value / "public" / "javascripts" / "jatos.js"

  val rendered = IO.read(source).replace("@JATOS_VERSION@", version.value)

  IO.write(target, rendered)

  Seq(target)
}.taskValue

Compile / unmanagedResources / excludeFilter := {
  val previous = (Compile / unmanagedResources / excludeFilter).value
  previous || "jatos.js"
}

libraryDependencies ++= Seq(
  guice,
  javaWs,
  javaJpa,
  "org.apache.commons" % "commons-collections4" % "4.5.0"
) ++ testDependencies