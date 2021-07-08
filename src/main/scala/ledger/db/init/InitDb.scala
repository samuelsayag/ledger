package ledger.db.init

import ledger.business.error.DomainError
import ledger.business.model.{AccountId, Amount, UserId}
import ledger.db.{Entities, Profile}
import slick.interop.zio.DatabaseProvider
import slick.interop.zio.syntax._
import slick.jdbc.JdbcProfile
import zio.clock.Clock
import zio.console._
import zio.duration._
import zio.{Has, RIO, Schedule, Task, UIO, ZIO, ZLayer}

import scala.concurrent.ExecutionContext

trait InitDb {

  /** make a simple request on account table for instance
    * to check that the db is readily accessible
    * @return the number of row in the account table
    */
  def checkbase(): Task[Int]

  /** perform the creabase:
    *  - create the schema
    *  - insert a cash account with 0 in the amount
    * @return nothing for the moment...
    */
  def creabase(): Task[Unit]

  def creabaseDebug(): UIO[String]
}

object InitDb {

  def checkbase(): RIO[Has[InitDb], Int] =
    ZIO.serviceWith[InitDb](_.checkbase())

  def creabase(): RIO[Has[InitDb], Unit] =
    ZIO.serviceWith[InitDb](_.creabase())

  def creabaseDebug(): RIO[Has[InitDb], String] =
    ZIO.serviceWith[InitDb](_.creabaseDebug())

  // TODO - more robust fail connection detection
  // TODO - use zio logging here
  // TODO - configurable space schedule
  // TODO - make it generic with a parameter
  def checkConnection(): ZIO[Has[InitDb] with Console with Clock, Exception, Int] = {
    val notAvailable = (_: String).contains("Connection is not available")
    val spaced1Sec   = Schedule.spaced(1.second)
    checkbase()
      .refineOrDie {
        case e: Exception if notAvailable(e.getMessage) => e
      }
      .tapError(_ => putStrLn(s"Database not available..."))
      .retry(spaced1Sec)
  }

  def createSchemaIfMissing(): RIO[Has[InitDb] with Console with Clock, Unit] =
    checkConnection() *>
      creabase().retry(Schedule.recurs(3) && Schedule.spaced(1.second)) *>
      ZIO.unit

  val initDb: ZIO[Has[InitDb] with Console with Clock, DomainError, Unit] =
    ((InitDb.creabaseDebug() >>= (schema => putStrLn(schema))) *>
      InitDb.createSchemaIfMissing() *>
      putStrLn(s"Schema created or was present..."))
      .mapError(th => DomainError.RepositoryError(new Exception(th)))

}

case class InitDbLive(
    // TODO - see if the parameter can be define such that
    //  only the DatabaseProvider is required
    dbp: DatabaseProvider,
    profile: JdbcProfile
) extends InitDb
    with Entities
    with Profile {

  import profile.api._

  override def checkbase(): Task[Int] =
    ZIO.fromDBIO(sql"""select 1""".as[Int].head).provide(Has(dbp))

  override def creabase(): Task[Unit] =
    ZIO
      .fromDBIO((ec: ExecutionContext) =>
        DBIO.seq(
          schemaBase.createIfNotExists,
          createCashIfNotExist(ec)
        )
      )
      .provide(Has(dbp))

  override def creabaseDebug(): UIO[String] =
    ZIO.succeed(
      schemaBase.createStatements.toList.mkString(System.lineSeparator())
    )

  private def createCashIfNotExist(implicit ec: ExecutionContext) = {
    for {
      accs <- accounts.filter(acc => acc.userId.isEmpty && acc.accountType === "CASH").result
      _ <- accs.headOption match {
        case Some(_) => accounts.filter(_.userId.isEmpty).map(_.id).result
        case None =>
          accounts returning accounts.map(_.id) += (AccountId(0L), Amount(
            BigDecimal(0)
          ), "CASH", Option.empty[UserId])
      }
    } yield ()
  }

  private val schemaBase =
    users.schema ++
      accounts.schema ++
      transactions.schema ++
      postings.schema
}

object InitDbLive {

  val layer: ZLayer[Has[DatabaseProvider], Throwable, Has[InitDb]] =
    ZLayer.fromServiceM { dbp =>
      for {
        profile <- dbp.profile
      } yield InitDbLive(dbp, profile)
    }
}
