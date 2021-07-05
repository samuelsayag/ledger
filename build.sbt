import Dependencies._

inThisBuild(
  List(
    scalaVersion := "2.13.5",
    version := "0.1.0-SNAPSHOT",
    organization := "com.unit",
    organizationName := "ledger",
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    scalacOptions ++= Seq(
      "-Wunused:imports",
      "-feature",
      "-deprecation",
      "-encoding",
      "utf8",
      "-Xfatal-warnings",
      "-deprecation",
      "-unchecked",
      "-language:implicitConversions",
      "-language:higherKinds",
      "-language:existentials",
      "-language:postfixOps"
    ),
    scalafixScalaBinaryVersion := "2.13",
    scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.5.0",
    fork := true
  )
)

lazy val root = (project in file("."))
  .settings(
    name := "ledger",
    libraryDependencies ++= Seq(
      zio,
      `zio-prelude`,
      `zio-test`,
      `zio-test-sbt`,
      `zio-test-magnolia`,
      slick,
      `slick-hikaricp`,
      `zio-slick-interop`,
      postgres,
      logback
    )
  )

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
