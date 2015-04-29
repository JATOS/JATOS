import com.typesafe.config._

val conf = ConfigFactory.parseFile(new File("conf/application.conf")).resolve()

version := conf.getString("application.version")

name := "JATOS"

organization := "org.jatos"

libraryDependencies ++= Seq(
	javaCore,
	javaJdbc,
	javaJpa,
	"org.hibernate" % "hibernate-entitymanager" % "4.2.15.Final",
	"mysql" % "mysql-connector-java" % "5.1.18",
	"com.google.inject" % "guice" % "3.0",
	"org.jsoup" % "jsoup" % "1.8.1",
	"commons-io" % "commons-io" % "2.4",
	"org.mockito" % "mockito-core" % "1.9.5" % "test",
	"org.webjars" %% "webjars-play" % "2.2.2-1",
	"org.webjars" % "bootstrap" % "3.3.4"
)

play.Project.playJavaSettings

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


