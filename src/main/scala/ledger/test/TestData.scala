package ledger.test

import ledger.business.LedgerRepository
import ledger.business.error.DomainError
import ledger.business.model._
import zio._
import zio.console._

object TestLedger {

  import TestData._

  val testDataInsert: ZIO[Has[LedgerRepository] with Console, DomainError, Unit] = {
    for {
      accTed              <- createAccount(ted)
      accBobAliceEveCarol <- createAccounts(users)
      List(accBob, accAlice, _, _) = accBobAliceEveCarol.toList
      _ <- tranDeposit(ted, accTed.number, Amount(2000))
      _ <- transDeposit(
        ted,
        accTed.number,
        List(Amount(100), Amount(200), Amount(300), Amount(400))
      )
      _ <- tranWithdraw(ted, accTed.number, Amount(750))
      _ <- transWithdraw(ted, accTed.number, List(Amount(125), Amount(75), Amount(50)))
      _ <- tranBook(ted, accBob.number, Amount(100))
      _ <- transBook(ted, accAlice.number, List(Amount(25), Amount(50), Amount(125)))
      _ <- getBalance(ted) >>= (b =>
        putStrLn(s"Balance of $ted: [$b]").mapError(th => DomainError.RepositoryError(th))
      )
      _ <- getBalance(bob) >>= (b =>
        putStrLn(s"Balance of $bob: [$b]").mapError(th => DomainError.RepositoryError(th))
      )
      _ <- getBalance(alice) >>= (b =>
        putStrLn(s"Balance of $alice: [$b]").mapError(th => DomainError.RepositoryError(th))
      )
      _ <- getPostings(ted) >>= (ps =>
        ZIO.foreach(ps)(p => putStrLn(s"$p").mapError(th => DomainError.RepositoryError(th)))
      )
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
    LedgerRepository.doTransaction(deposit(userData, accountId, amount))

  def transDeposit(
      userData: UserData,
      accountId: AccountId,
      amounts: Iterable[Amount]
  ): ZIO[Has[LedgerRepository], DomainError, Unit] =
    ZIO.foreach(amounts)(am =>
      LedgerRepository.doTransaction(deposit(userData, accountId, am))
    ) *> ZIO.unit

  def tranWithdraw(
      userData: UserData,
      accountId: AccountId,
      amount: Amount
  ): ZIO[Has[LedgerRepository], DomainError, Unit] =
    LedgerRepository.doTransaction(withdraw(userData, accountId, amount))

  def transWithdraw(
      userData: UserData,
      accountId: AccountId,
      amounts: Iterable[Amount]
  ): ZIO[Has[LedgerRepository], DomainError, Unit] =
    ZIO.foreach(amounts)(am =>
      LedgerRepository.doTransaction(withdraw(userData, accountId, am))
    ) *> ZIO.unit

  def tranBook(
      userData: UserData,
      accountId: AccountId,
      amount: Amount
  ): ZIO[Has[LedgerRepository], DomainError, Unit] =
    LedgerRepository.doTransaction(book(userData, accountId, amount))

  def transBook(
      userData: UserData,
      accountId: AccountId,
      amounts: Iterable[Amount]
  ): ZIO[Has[LedgerRepository], DomainError, Unit] =
    ZIO.foreach(amounts)(am =>
      LedgerRepository.doTransaction(book(userData, accountId, am))
    ) *> ZIO.unit

  def getBalance(userData: UserData): ZIO[Has[LedgerRepository], DomainError, Amount] =
    LedgerRepository.getBalance(userData)

  def getPostings(userData: UserData): ZIO[Has[LedgerRepository], DomainError, List[LedgerLine]] =
    LedgerRepository.getTransactions(userData)
}

object TestData {

  val ted: UserData   = UserData("Ted")
  val bob: UserData   = UserData("Bob")
  val alice: UserData = UserData("Alice")

  val users: List[UserData] =
    bob :: alice :: UserData("Eve") :: UserData("Carol") :: Nil

  def deposit(userData: UserData, acc: AccountId, amount: Amount): TransactionData =
    TransactionData(userData, acc, TransactionType.Deposit, amount)

  def withdraw(userData: UserData, acc: AccountId, amount: Amount): TransactionData =
    TransactionData(userData, acc, TransactionType.Withdraw, amount)

  def book(userData: UserData, acc: AccountId, amount: Amount): TransactionData =
    TransactionData(userData, acc, TransactionType.Book, amount)
}
