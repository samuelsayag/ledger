package ledger.db

import ledger.business.error.DomainError
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
        new LedgerRepository with DBIOHelper with Entities with Profile {

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
              .mapError(DomainError.fromThrowable)

            (for {
              userAndAccount <- createAccount
              (user, (accountId, balance, accountType, _)) = userAndAccount
              accountType <- AccountType.make(accountType, Some(user))
            } yield Account(accountId, balance, accountType)).provide(Has(dbp))
          }

          override def doTransaction(
              trans: model.TransactionData
          ): IO[error.DomainError, Unit] = {

            val func = (ec: ExecutionContext) => {
              implicit val ex: ExecutionContext = ec
              (for {
                transId          <- insertTransaction(trans)
                unitCashAccounts <- getUnitCashAccount.map(_.id).result
                unitCashAccount = unitCashAccounts.head
                userAccountIds <- getUserAccountId(trans.user).result
                userAccountId = userAccountIds.head
                transactions <- getTransaction(transId).result
                tran = transactions.map { case (transId, accId, transType, amount) =>
                  Transaction(transId, accId, TransactionType.fromString(transType), amount)
                }.head
                postings <- Posting
                  .fromTransaction(tran, Some(unitCashAccount), Some(userAccountId))
                  .toDBIO
                _ <- insertPostings(transId, postings)
                _ <- DBIO.sequence(postings.map(p => updateBalance(p)))
              } yield ()).transactionally
            }

            (for {
              _ <- checkBalance(trans)
              _ <- ZIO.fromDBIO(func).mapError(th => DomainError.RepositoryError(new Exception(th)))
            } yield ()).provide(Has(dbp))
          }

          override def getAccount(user: UserData): IO[DomainError, Account] =
            (for {
              accRes <- ZIO
                .fromDBIO(getUserAccount(user).result.head)
                .mapError(DomainError.fromThrowable)
              (accId, amount, accType, optUserid) = accRes
              accountType <- optUserid match {
                case Some(userId) => AccountType.make(accType, Some(User(userId, user.name)))
                case None =>
                  ZIO.fail(
                    DomainError.fromMsg(s"Deposit Account must have userId. UserData [$user]")
                  )
              }
            } yield Account(accId, amount, accountType)).provide(Has(dbp))

          override def getBalance(
              user: model.UserData
          ): IO[error.DomainError, model.Amount] = ZIO
            .fromDBIO(getBalanceQuery(user).result.head)
            .mapError(DomainError.fromThrowable)
            .provide(Has(dbp))

          override def getTransactions(user: UserData): IO[DomainError, List[LedgerLine]] = {
            def rawLedger(accountId: AccountId) =
              for {
                (tran, post) <- transactions join
                  postings on
                  (_.id === _.transactionId) filter (_._2.accountId =!= accountId)
              } yield (tran, post)

            val func = (ec: ExecutionContext) => {
              implicit val ex: ExecutionContext = ec
              for {
                userAccounts <- getUserAccountId(user).result
                userAccount = userAccounts.head
                tranPost <- rawLedger(userAccount).result
              } yield tranPost.map {
                case ((transId, accId1, transType, amount), (id, tranId, accId2, credit, debit)) =>
                  LedgerLine.from(
                    Transaction(transId, accId1, TransactionType.fromString(transType), amount),
                    Posting.from(id, tranId, accId2, credit, debit)
                  )
              }.toList
            }

            ZIO
              .fromDBIO(func)
              .mapError(th => DomainError.RepositoryError(new Exception(th)))
              .provide(Has(dbp))
          }

          private val getUnitCashAccount = accounts.filter(_.userId.isEmpty)

          private def getTransaction(id: TransactionId) = transactions.filter(_.id === id)

          private def insertTransaction(data: TransactionData) =
            transactions returning transactions.map(_.id) +=
              (dummyTransactionId, AccountId(data.accountNumber), TransactionType.asString(
                data.transactionType
              ), data.amount)

          private def getUserAccount(userData: UserData) =
            for {
              (_, a) <-
                users join accounts on (_.id === _.userId) filter (_._1.name === userData.name)
            } yield a

          private def getUserId(userData: UserData) =
            users.filter(_.name === userData.name)

          private def getUserAccountId(userData: UserData) =
            getUserAccount(userData).map(_.id)

          private def getBalanceQuery(userData: UserData) =
            getUserAccount(userData).map(_.balance)

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

          private def updateBalance(post: PostingData)(implicit ec: ExecutionContext) = {
            for {
              currBalance <- accounts.filter(_.id === post.accountNumber).map(_.balance).result
              newBalance = Amount(currBalance.head + post.amount)
              _ <- updateBalanceQuery(post.accountNumber, newBalance)
            } yield ()
          }

          private def updateBalanceQuery(accountId: AccountId, newBalance: Amount) =
            (for {
              acc <- accounts.filter(_.id === accountId)
            } yield acc.balance).update(newBalance)

          private def checkBalance(
              trans: model.TransactionData
          ): IO[DomainError, Amount] =
            if (
              trans.transactionType == TransactionType.Withdraw ||
              trans.transactionType == TransactionType.Book
            )
              getBalance(trans.user).flatMap(amount =>
                if (amount - trans.amount < 0)
                  ZIO.fail(
                    DomainError.RepositoryError(
                      new Exception(
                        s"Cannot reach negative balance [trans: ${trans.amount}] [balance: $amount]"
                      )
                    )
                  )
                else ZIO.succeed(trans.amount)
              )
            else ZIO.succeed(trans.amount)
        }
      }
    }
}
