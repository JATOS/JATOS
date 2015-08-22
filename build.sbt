import com.typesafe.config._

version := "2.1.1-beta"

name := "JATOS"

organization := "org.jatos"

scalaVersion := "2.11.6"

lazy val root = (project in file(".")).enablePlugins(PlayJava, SbtWeb)

libraryDependencies ++= Seq(
	javaCore,
	javaJdbc,
	javaJpa,
	javaWs,
	"org.hibernate" % "hibernate-entitymanager" % "4.2.15.Final",
	"mysql" % "mysql-connector-java" % "5.1.18",
	"com.google.inject" % "guice" % "4.0",
	"org.jsoup" % "jsoup" % "1.8.1",
	"commons-io" % "commons-io" % "2.4",
	"org.mockito" % "mockito-core" % "1.9.5" % "test",
	"org.webjars" %% "webjars-play" % "2.3.0-2",
	"org.webjars" % "bootstrap" % "3.3.4"
)

javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint")

initialize := {
  val _ = initialize.value
  if (sys.props("java.specification.version") != "1.8")
    sys.error("Java 8 is required for this project.")
}

includeFilter in (Assets, LessKeys.less) := "*.less"

excludeFilter in (Assets, LessKeys.less) := "_*.less"

mappings in Universal += file(baseDirectory.value + "/loader.sh") -> ("loader.sh")

mappings in Universal += file(baseDirectory.value + "/loader.bat") -> ("loader.bat")

mappings in Universal := (mappings in Universal).value filter { 
	case (file, path) => ! path.endsWith(".development.conf")
}

mappings in Universal := (mappings in Universal).value filter { 
	case (file, path) => ! path.endsWith(".testing.conf")
}

mappings in Universal := (mappings in Universal).value filter { 
	case (file, path) => ! path.endsWith("jatos.bat")
}

mappings in Universal := (mappings in Universal).value filter { 
	case (file, path) => ! path.contains("share/doc")
}

Keys.fork in Test := false

