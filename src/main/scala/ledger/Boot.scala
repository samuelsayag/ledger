package ledger

import com.typesafe.config.{Config, ConfigFactory}
import ledger.db.init.{InitDb, InitDbLive}
import slick.interop.zio.DatabaseProvider
import slick.jdbc.JdbcProfile
import zio.{App => ZIOApp, _}
import zio.console._
import zio.clock._

object Boot extends ZIOApp {

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    ZIO(ConfigFactory.load.resolve)
      .flatMap(typeSafeConfig =>
        program.provideCustomLayer(appEnvironment(typeSafeConfig))
      )
      .exitCode

  lazy val program = initDb

  lazy val initDb: RIO[Has[InitDb] with Console with Clock, Int] = {
    val debugSchema = InitDb.creabaseDebug() >>= (schema => putStrLn(schema))
    debugSchema *>
      InitDb.checkConnection() *>
      InitDb.createSchemaIfMissing() *>
      putStrLn(s"Create the schema") *>
      ZIO.succeed(1)
  }

  def appEnvironment(typeSafeconfig: Config): TaskLayer[Has[InitDb]] = {

    val dbConfigLayer: TaskLayer[Has[Config]] =
      ZLayer.fromEffect(ZIO(typeSafeconfig.getConfig("db")))

    val dbBackendLayer: ULayer[Has[JdbcProfile]] =
      ZLayer.succeed[JdbcProfile](slick.jdbc.PostgresProfile)

    val dbProvider: TaskLayer[Has[DatabaseProvider]] =
      (dbConfigLayer ++ dbBackendLayer) >>> DatabaseProvider.live

    dbProvider >>> InitDbLive.layer
  }

}
