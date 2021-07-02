import sbt._

object Dependencies {
  val slickVersion = "3.3.3"
  val zioVersion = "1.0.9"

  lazy val zio = "dev.zio" %% "zio" % zioVersion
  lazy val `zio-prelude` = "dev.zio" %% "zio-prelude" % "1.0.0-RC5"
  lazy val `zio-test` = "dev.zio" %% "zio-test" % zioVersion % Test
  lazy val `zio-test-sbt` = "dev.zio" %% "zio-test-sbt" % zioVersion % Test
  lazy val `zio-test-magnolia` =
    "dev.zio" %% "zio-test-magnolia" % zioVersion % Test // optional
  lazy val slick = "com.typesafe.slick" %% "slick" % slickVersion
  lazy val `slick-hikaricp` =
    "com.typesafe.slick" %% "slick-hikaricp" % slickVersion
  lazy val postgres = "org.postgresql" % "postgresql" % "42.2.22"
  lazy val logback = "ch.qos.logback" % "logback-classic" % "1.2.3"

  lazy val `zio-slick-interop` = "io.scalac" %% "zio-slick-interop" % "0.3.0"
}
