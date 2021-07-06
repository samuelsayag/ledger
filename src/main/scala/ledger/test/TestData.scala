package ledger.test

import ledger.business.LedgerRepository
import ledger.business.error.DomainError
import ledger.business.model._
import zio._

object TestLedger {

  import TestData._

  val testDataInsert: ZIO[Has[LedgerRepository], DomainError, Unit] = {
    for {
      accTed              <- createAccount(ted)
      accBobAliceEveCarol <- createAccounts(users)
      List(accBob, accAlice, accEve, accCarol) = accBobAliceEveCarol.toList
      _ <- tranDeposit(ted, accTed.number, Amount(1000))
      _ <- tranDeposits(ted, accTed.number, List(Amount(100), Amount(200), Amount(300)))
    } yield ()
  }

  def createAccount(userData: UserData): ZIO[Has[LedgerRepository], DomainError, Account] =
    LedgerRepository.createDepositAccount(userData)

  def createAccounts(
      userDatas: Iterable[UserData]
  ): ZIO[Has[LedgerRepository], DomainError, Iterable[Account]] =
    ZIO.foreach(userDatas)(LedgerRepository.createDepositAccount)

  def tranDeposit(
      userData: UserData,
      accountId: AccountId,
      amount: Amount
  ): ZIO[Has[LedgerRepository], DomainError, Unit] =
    LedgerRepository.doTransaction(userData, deposit(userData, accountId, amount))

  def tranDeposits(
      userData: UserData,
      accountId: AccountId,
      amounts: Iterable[Amount]
  ): ZIO[Has[LedgerRepository], DomainError, Unit] =
    ZIO.foreach(amounts)(am =>
      LedgerRepository.doTransaction(userData, deposit(userData, accountId, am))
    ) *>
      ZIO.unit

}

object TestData {

  val ted: UserData = UserData("Ted")

  val users: List[UserData] =
    UserData("Bob") ::
      UserData("Alice") ::
      UserData("Eve") ::
      UserData("Carol") :: Nil

  def deposit(userData: UserData, acc: AccountId, amount: Amount): TransactionData =
    TransactionData(userData, acc, TransactionType.Deposit, amount)

  def withdraw(userData: UserData, acc: AccountId, amount: Amount): TransactionData =
    TransactionData(userData, acc, TransactionType.Deposit, amount)

  def book(userData: UserData, acc: AccountId, amount: Amount): TransactionData =
    TransactionData(userData, acc, TransactionType.Deposit, amount)
}
