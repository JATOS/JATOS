import sbt._
import Keys._

object Common {
	val settings: Seq[Setting[_]] = Seq(
		version := "3.1.7",
		organization := "org.jatos",
		scalaVersion := "2.11.8"
	)
}