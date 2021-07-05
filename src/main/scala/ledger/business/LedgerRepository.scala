package ledger.business

import zio._
import ledger.business.model.{Account, Amount, Posting, Transaction, TransactionData, UserData}
import ledger.business.error.DomainError

trait LedgerRepository {

  def createDepositAccount(user: UserData): IO[DomainError, Account]

  def doTransaction(user: UserData, trans: TransactionData): IO[DomainError, Posting]

  def getBalance(user: UserData): IO[DomainError, Amount]

  def getTransactions(user: UserData): IO[DomainError, List[Transaction]]
}

object LedgerRepository {

  def createDepositAccount(
      user: UserData
  ): ZIO[Has[LedgerRepository], DomainError, Account] =
    ZIO.accessM(_.get.createDepositAccount(user))

  def doTransaction(
      user: UserData,
      trans: TransactionData
  ): ZIO[Has[LedgerRepository], DomainError, Posting] =
    ZIO.accessM(_.get.doTransaction(user, trans))

  def getBalance(
      user: UserData
  ): ZIO[Has[LedgerRepository], DomainError, Amount] =
    ZIO.accessM(_.get.getBalance(user))

  def getTransactions(
      user: UserData
  ): ZIO[Has[LedgerRepository], DomainError, List[Transaction]] =
    ZIO.accessM(_.get.getTransactions(user))
}
