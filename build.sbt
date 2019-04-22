name := "StackTraceT"

version := "0.1"

scalaVersion := "2.12.8"

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-effect" % "1.2.0",
)

addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.0")