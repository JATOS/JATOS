import com.typesafe.config._

val conf = ConfigFactory.parseFile(new File("conf/application.conf")).resolve()

version := conf.getString("application.version")

name := "JATOS"

scalaVersion := "2.11.1"

libraryDependencies ++= Seq(
	javaCore,
	javaJdbc,
	javaJpa,
	javaWs,
	"org.hibernate" % "hibernate-entitymanager" % "4.2.15.Final",
	"mysql" % "mysql-connector-java" % "5.1.18",
	"com.google.inject" % "guice" % "3.0",
	"org.jsoup" % "jsoup" % "1.8.1",
	"commons-io" % "commons-io" % "2.4",
	"org.mockito" % "mockito-core" % "1.9.5" % "test",
	"org.webjars" %% "webjars-play" % "2.3.0-2",
	"org.webjars" % "bootstrap" % "3.3.0"
)

lazy val root = (project in file(".")).enablePlugins(PlayJava, SbtWeb)

includeFilter in (Assets, LessKeys.less) := "*.less"

excludeFilter in (Assets, LessKeys.less) := "_*.less"

mappings in Universal += file(baseDirectory.value + "/loader.sh") -> ("loader.sh")

mappings in Universal += file(baseDirectory.value + "/loader.bat") -> ("loader.bat")

mappings in Universal := (mappings in Universal).value filter { case (file, path) => 
	! path.endsWith(".development.conf")
}

Keys.fork in Test := false




fork in run := true