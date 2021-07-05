package ledger.business

import zio._
import ledger.business.model.Account
import ledger.business.error.DomainError
import ledger.business.model.Posting
import ledger.business.model.UserData
import ledger.business.model.TransactionData
import ledger.business.model.AccountId

trait LedgerRepository {

  def createDepositAccount(user: UserData): IO[DomainError, Account]

  def doTransaction(user: UserData, trans: TransactionData): IO[DomainError, Posting]

  def getBalance(user: UserData, account: AccountId): IO[DomainError, Account]
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
      user: UserData,
      account: AccountId
  ): ZIO[Has[LedgerRepository], DomainError, Account] =
    ZIO.accessM(_.get.getBalance(user, account))
}
