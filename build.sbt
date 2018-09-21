name := "finagle-examples"


val commonSettings = Seq(
  version := "1.0",

  scalaVersion := "2.12.6",

  scalacOptions := Seq(
    "-encoding",
    "utf8",
    "-feature",
    "-unchecked",
    "-deprecation",
    "-target:jvm-1.8",
    "-language:_",
    "-Xexperimental"),

  libraryDependencies += "com.twitter" %% "finagle-http" % "18.8.0"
)

val root = project.in(file(".")).settings(commonSettings)
val example1 = project.in(file("./example1")).settings(commonSettings)
val example2 = project.in(file("./example2")).settings(commonSettings)
val example3 = project.in(file("./example3")).settings(commonSettings)
val example4 = project.in(file("./example4")).settings(commonSettings)
val example5 = project.in(file("./example5")).settings(commonSettings)
val example6 = project.in(file("./example6")).settings(commonSettings)