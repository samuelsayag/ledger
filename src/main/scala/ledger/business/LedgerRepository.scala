package ledger.business

import ledger.business.error.DomainError
import ledger.business.model._
import zio._

trait LedgerRepository {

  def createDepositAccount(user: UserData): IO[DomainError, Account]

  def doTransaction(trans: TransactionData): IO[DomainError, Unit]

  def getBalance(user: UserData): IO[DomainError, Amount]

  def getAccount(user: UserData): IO[DomainError, Account]

  def getTransactions(user: UserData): IO[DomainError, List[LedgerLine]]
}

object LedgerRepository {

  def createDepositAccount(
      user: UserData
  ): ZIO[Has[LedgerRepository], DomainError, Account] =
    ZIO.accessM(_.get.createDepositAccount(user))

  def doTransaction(
      trans: TransactionData
  ): ZIO[Has[LedgerRepository], DomainError, Unit] =
    ZIO.accessM(_.get.doTransaction(trans))

  def getBalance(
      user: UserData
  ): ZIO[Has[LedgerRepository], DomainError, Amount] =
    ZIO.accessM(_.get.getBalance(user))

  def getAccount(
      user: UserData
  ): ZIO[Has[LedgerRepository], DomainError, Account] =
    ZIO.accessM(_.get.getAccount(user))

  def getTransactions(
      user: UserData
  ): ZIO[Has[LedgerRepository], DomainError, List[LedgerLine]] =
    ZIO.accessM(_.get.getTransactions(user))
}
