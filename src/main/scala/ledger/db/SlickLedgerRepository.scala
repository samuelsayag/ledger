package ledger.db

import ledger.business.error.DomainError
import ledger.business.error.DomainError.RepositoryError
import ledger.business.model.{AccountId, Amount, TransactionId, _}
import ledger.business.{LedgerRepository, error, model}
import slick.interop.zio.DatabaseProvider
import slick.interop.zio.syntax._
import zio.{Has, IO, ZIO, ZLayer}

import scala.concurrent.ExecutionContext

object SlickLedgerRepository {

  val live: ZLayer[Has[DatabaseProvider], Throwable, Has[LedgerRepository]] =
    ZLayer.fromServiceM[DatabaseProvider, Any, Throwable, LedgerRepository] { dbp =>
      dbp.profile.map { prof =>
        new LedgerRepository with Entities with Profile {

          override lazy val profile = prof
          import profile.api._

          val dummyId: Long                     = 0L
          val dummyUserId: UserId               = UserId(dummyId)
          val dummyAccountId: AccountId         = AccountId(dummyId)
          val dummyTransactionId: TransactionId = TransactionId(dummyId)
          val dummyPostingId: PostingId         = PostingId(dummyId)

          override def createDepositAccount(
              userData: model.UserData
          ): IO[error.DomainError, model.Account] = {

            // TODO - work on a way to constrain this ("DEPOSIT")
            def insAcc(userId: UserId) =
              (accounts returning accounts.map(_.id)) +=
                (dummyAccountId, Amount(BigDecimal(0.0)), "DEPOSIT", Some(userId))

            def selUserAndInsAcc(ex: ExecutionContext) = {
              implicit val ec: ExecutionContext = ex
              for {
                users <- getUserId(userData).result
                user = users.head
                accountId <- insAcc(user.id)
                accounts  <- accounts.filter(_.id === accountId).result
              } yield (user, accounts.head)
            }

            def insUserAndInsAccount(ex: ExecutionContext) = {
              implicit val ec: ExecutionContext = ex
              val user                          = User(dummyUserId, userData.name)
              for {
                userId    <- (users returning users.map(_.id)) += user
                accountId <- insAcc(userId)
                accounts  <- accounts.filter(_.id === accountId).result
              } yield (user.copy(id = userId), accounts.head)
            }

            val createAccount = ZIO
              .fromDBIO(selUserAndInsAcc _)
              .orElse(ZIO.fromDBIO(insUserAndInsAccount _))

            (for {
              userAndAccount <- createAccount
              (user, (accountId, balance, accountType, _)) = userAndAccount
              accountType <- AccountType.make(accountType, Some(user))
            } yield Account(accountId, balance, accountType))
              .refineOrDie { case e: Exception =>
                RepositoryError(e): DomainError
              }
              .provide(Has(dbp))
          }

          override def doTransaction(
              user: UserData,
              trans: model.TransactionData
          ): IO[error.DomainError, model.Posting] = {

            ???
          }

          private def insertTransaction(data: TransactionData) =
            transactions returning transactions.map(_.id) +=
              (dummyTransactionId, AccountId(data.accountNumber), TransactionType.asString(
                data.transactionType
              ), data.amount)

          private def getUserId(userData: UserData) =
            users.filter(_.name === userData.name)

          private def insertPostings(tranId: TransactionId, data: List[PostingData]) =
            postings ++= data.map(pd =>
              pd.transferType match {
                case TransferType.Credit =>
                  (
                    dummyPostingId,
                    tranId,
                    pd.accountNumber,
                    Option(pd.amount),
                    Option.empty[Amount]
                  )
                case TransferType.Debit =>
                  (
                    dummyPostingId,
                    tranId,
                    pd.accountNumber,
                    Option.empty[Amount],
                    Option(pd.amount)
                  )
              }
            )

          private def checkBalance(userData: UserData) =
            for {
              (_, a) <-
                users join accounts on (_.id === _.userId) filter (_._1.name === userData.name)
            } yield a.balance

          override def getBalance(
              user: model.UserData
          ): IO[error.DomainError, model.Amount] = ZIO
            .fromDBIO(checkBalance(user).result.head)
            .provide(Has(dbp))
            .mapError(th => DomainError.RepositoryError(new Exception(th)))

          override def getTransactions(user: UserData): IO[DomainError, List[Transaction]] = ???
        }
      }
      ???
    }
}
