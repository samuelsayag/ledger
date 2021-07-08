package ledger.app

import ledger.business.LedgerRepository
import ledger.business.error.DomainError
import ledger.business.model._
import zio._

trait App {

  def createDepositAccount(user: UserData): IO[DomainError, AccountId]

  def doTransaction(transactionRequest: TransactionRequest): IO[DomainError, Unit]

  def getBalance(user: UserData): IO[DomainError, Account]

  def getTransactions(user: UserData): IO[DomainError, List[LedgerLine]]

}

object App {

  val live: URLayer[Has[LedgerRepository], Has[App]] =
    ZLayer.fromService { ledgerRepo =>
      import TransactionRequest._
      new App {
        override def createDepositAccount(user: UserData): IO[DomainError, AccountId] = {
          for {
            acc <- ledgerRepo.getAccount(user.normalize()).map(Some(_)).orElse(ZIO.succeed(None))
            accCreation <-
              acc match {
                case None => ledgerRepo.createDepositAccount(user.normalize()).map(_.number)
                case Some(_) =>
                  ZIO.fail(DomainError.ValidationError(s"User [$user] already has an account"))
              }

          } yield accCreation
        }

        override def doTransaction(transactionRequest: TransactionRequest): IO[DomainError, Unit] =
          (transactionRequest match {
            case Deposit(name, amount) =>
              val userData = UserData(name)
              ledgerRepo
                .getAccount(userData)
                .map(acc => TransactionData(userData, acc.number, TransactionType.Deposit, amount))

            case Withdraw(name, amount) =>
              val userData = UserData(name)
              ledgerRepo
                .getAccount(userData)
                .map(acc => TransactionData(userData, acc.number, TransactionType.Withdraw, amount))

            case Book(name, amount, accountId) =>
              val userData = UserData(name)
              ZIO.succeed(
                TransactionData(userData, accountId, TransactionType.Withdraw, amount)
              )
          }).flatMap(ledgerRepo.doTransaction)

        override def getBalance(user: UserData): IO[DomainError, Account] =
          ledgerRepo.getAccount(user)

        override def getTransactions(user: UserData): IO[DomainError, List[LedgerLine]] =
          ledgerRepo.getTransactions(user)
      }
    }

  def createDepositAccount(user: UserData): ZIO[Has[App], DomainError, AccountId] =
    ZIO.accessM(_.get.createDepositAccount(user))

  def doTransaction(transactionRequest: TransactionRequest): ZIO[Has[App], DomainError, Unit] =
    ZIO.accessM(_.get.doTransaction(transactionRequest))

  def getBalance(user: UserData): ZIO[Has[App], DomainError, Account] =
    ZIO.accessM(_.get.getBalance(user))

  def getTransactions(user: UserData): ZIO[Has[App], DomainError, List[LedgerLine]] =
    ZIO.accessM(_.get.getTransactions(user))

}
