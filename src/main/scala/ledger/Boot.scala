package ledger

import com.typesafe.config.{Config, ConfigFactory}
import ledger.business.LedgerRepository
import ledger.db.SlickLedgerRepository
import ledger.db.init.{InitDb, InitDbLive}
import ledger.test.TestLedger
import slick.interop.zio.DatabaseProvider
import slick.jdbc.JdbcProfile
import zio.{App => ZIOApp, _}
import ledger.business.error
import zio.clock.Clock

object Boot extends ZIOApp {

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    ZIO(ConfigFactory.load.resolve)
      .flatMap(typeSafeConfig => program.provideCustomLayer(appEnvironment(typeSafeConfig)))
      .exitCode

  lazy val program = initDb

  lazy val initDb: ZIO[Has[InitDb] with zio.console.Console with Clock with Has[
    LedgerRepository
  ], error.DomainError, Int] =
    InitDb.initDb *>
      TestLedger.testDataInsert *>
      ZIO.succeed(1)

  //lazy val initDb: RIO[Has[InitDb] with Console with Clock, Int] =
  //  InitDb.initDb *> ZIO.succeed(1)

  def appEnvironment(typeSafeconfig: Config): TaskLayer[Has[InitDb] with Has[LedgerRepository]] = {

    val dbConfigLayer: TaskLayer[Has[Config]] =
      ZLayer.fromEffect(ZIO(typeSafeconfig.getConfig("db")))

    val dbBackendLayer: ULayer[Has[JdbcProfile]] =
      ZLayer.succeed[JdbcProfile](slick.jdbc.PostgresProfile)

    val dbProvider: TaskLayer[Has[DatabaseProvider]] =
      (dbConfigLayer ++ dbBackendLayer) >>> DatabaseProvider.live

    (dbProvider >>> InitDbLive.layer) ++
      (dbProvider >>> SlickLedgerRepository.live)
  }

}
