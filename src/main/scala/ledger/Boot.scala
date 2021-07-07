package ledger

import akka.actor.ActorSystem
import akka.http.interop.HttpServer
import akka.http.scaladsl.server.Route
import com.typesafe.config.{Config, ConfigFactory}
import ledger.api.Api
import ledger.app.App
import ledger.business.LedgerRepository
import ledger.db.SlickLedgerRepository
import ledger.db.init.{InitDb, InitDbLive}
import ledger.test.TestLedger
import slick.interop.zio.DatabaseProvider
import slick.jdbc.JdbcProfile
import zio.{App => ZIOApp, _}
import ledger.business.error
import zio.clock.Clock
import zio.console.putStrLn

object Boot extends ZIOApp {

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    program.provideCustomLayer(appEnvironment()).exitCode

  lazy val program = app

  lazy val app: ZIO[HttpServer with zio.console.Console with Has[InitDb] with zio.console.Console with Clock,Object,Nothing] = {
    val startHttpServer =
      HttpServer.start.tapM(_ => putStrLn("Server online."))

    (startHttpServer *> InitDb.initDb.toManaged_).useForever
  }

  lazy val testData: ZIO[Has[InitDb] with zio.console.Console with Clock with Has[
    LedgerRepository
  ], error.DomainError, Int] =
    InitDb.initDb *>
      TestLedger.testDataInsert *>
      ZIO.succeed(1)

  def appEnvironment()
      : TaskLayer[Has[InitDb] with Has[LedgerRepository] with Has[App] with HttpServer] = {

    val typeSafeConfig: TaskLayer[Has[Config]] =
      ZLayer.fromEffect(ZIO.succeed(ConfigFactory.load.resolve))

    val dbConf = typeSafeConfig.map(c => Has(c.get.getConfig("db")))

    val serverConf: TaskLayer[Has[HttpServer.Config]] =
      typeSafeConfig.map(c =>
        Has {
          val conf = c.get.getConfig("api")
          HttpServer.Config(
            conf.getString("host"),
            conf.getInt("port")
          )
        }
      )

    val dbBackendLayer: ULayer[Has[JdbcProfile]] =
      ZLayer.succeed[JdbcProfile](slick.jdbc.PostgresProfile)

    val dbProvider: TaskLayer[Has[DatabaseProvider]] =
      (dbConf ++ dbBackendLayer) >>> DatabaseProvider.live

    val initDbLayer = dbProvider >>> InitDbLive.layer
    val ledgerRepoLayer: TaskLayer[Has[LedgerRepository]] =
      dbProvider >>> SlickLedgerRepository.live

    val actorSystemLayer: TaskLayer[Has[ActorSystem]] = ZLayer.fromManaged {
      ZManaged.make(ZIO(ActorSystem("ledger-actor-system")))(s =>
        ZIO.fromFuture(_ => s.terminate()).either
      )
    }

    val appLayer: TaskLayer[Has[App]] = ledgerRepoLayer >>> App.live

    val routesLayer: TaskLayer[Has[Route]] =
      (appLayer >>> Api.live).map(app => Has(app.get.routes))

    val httpLayer: TaskLayer[HttpServer] =
      (actorSystemLayer ++ serverConf ++ routesLayer) >>>
        HttpServer.live

    initDbLayer ++ ledgerRepoLayer ++ appLayer ++ httpLayer
  }

}
