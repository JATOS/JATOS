import sbt._
import sbt.Keys._

object MockitoSettings {
  val settings: Seq[Def.Setting[_]] = Seq(
    libraryDependencies +=
      "org.mockito" % "mockito-core" % "5.23.0" % Test,

    Test / fork := true,

    Test / javaOptions ++= {
      val mockitoJar = (Test / dependencyClasspath).value
        .map(_.data)
        .find(_.getName.startsWith("mockito-core-"))
        .getOrElse(sys.error("mockito-core JAR not found on test classpath"))

      Seq(s"-javaagent:${mockitoJar.getAbsolutePath}")
    }
  )
}