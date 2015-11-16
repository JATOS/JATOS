import com.typesafe.config._

name := "jatos-gui"

version := "2.1.1-beta"

scalaVersion := "2.11.6"

includeFilter in (Assets, LessKeys.less) := "*.less"

excludeFilter in (Assets, LessKeys.less) := "_*.less"

libraryDependencies ++= Seq(
	"org.mockito" % "mockito-core" % "1.9.5" % "test",
	"org.webjars" % "bootstrap" % "3.3.4",
	"org.webjars" % "jquery" % "1.11.3"
)

routesGenerator := InjectedRoutesGenerator

Keys.fork in Test := false
