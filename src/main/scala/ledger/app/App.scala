package ledger.app

import ledger.business.LedgerRepository
import ledger.business.error.DomainError
import ledger.business.model._
import zio._

trait App {

  def createDepositAccount(user: UserData): IO[DomainError, AccountId]

  def doTransaction(transactionRequest: TransactionRequest): IO[DomainError, Unit]

  def getBalance(user: UserData): IO[DomainError, Amount]

  def getTransactions(user: UserData): IO[DomainError, List[LedgerLine]]

}

object App {

  val live: URLayer[Has[LedgerRepository], Has[App]] =
    ZLayer.fromService { ledgerRepo =>
      import TransactionRequest._
      new App {
        override def createDepositAccount(user: UserData): IO[DomainError, AccountId] =
          ledgerRepo.createDepositAccount(user).map(_.number)

        override def doTransaction(transactionRequest: TransactionRequest): IO[DomainError, Unit] =
          (transactionRequest match {
            case DepositTransaction(name, amount) =>
              val userData = UserData(name)
              ledgerRepo
                .getAccount(userData)
                .map(acc => TransactionData(userData, acc.number, TransactionType.Deposit, amount))

            case WithdrawTransaction(name, amount) =>
              val userData = UserData(name)
              ledgerRepo
                .getAccount(userData)
                .map(acc => TransactionData(userData, acc.number, TransactionType.Withdraw, amount))

            case BookTransaction(name, amount, accountId) =>
              val userData = UserData(name)
              ZIO.succeed(
                TransactionData(userData, accountId, TransactionType.Withdraw, amount)
              )
          }).flatMap(ledgerRepo.doTransaction)

        override def getBalance(user: UserData): IO[DomainError, Amount] =
          ledgerRepo.getBalance(user)

        override def getTransactions(user: UserData): IO[DomainError, List[LedgerLine]] =
          ledgerRepo.getTransactions(user)
      }
    }

  def createDepositAccount(user: UserData): ZIO[Has[App], DomainError, AccountId] =
    ZIO.accessM(_.get.createDepositAccount(user))

  def doTransaction(transactionRequest: TransactionRequest): ZIO[Has[App], DomainError, Unit] =
    ZIO.accessM(_.get.doTransaction(transactionRequest))

  def getBalance(user: UserData): ZIO[Has[App], DomainError, Amount] =
    ZIO.accessM(_.get.getBalance(user))

  def getTransactions(user: UserData): ZIO[Has[App], DomainError, List[LedgerLine]] =
    ZIO.accessM(_.get.getTransactions(user))

}
