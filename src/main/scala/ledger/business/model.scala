package ledger.business

import zio._
import zio.prelude._

package object model {

  // TODO - What is the relation of an account with a currency?

  /** - id: Could have restriction on the format (create new type for that)
    * - name: Could have restriction on the format (create new type for that)
    */
  object UserId extends Subtype[Long]
  type UserId = UserId.Type

  final case class User(id: UserId = UserId(0L), name: String)

  final case class UserData(name: String)

  /** Type of Accounts handle bu Unit
    * - Deposit: Unit liability
    * - Cash: Unit asset
    */
  sealed trait AccountType
  object AccountType {
    case object Cash extends AccountType {
      def make(s: String): IO[IllegalArgumentException, AccountType] =
        if (s.toUpperCase == "CASH") ZIO.succeed(Cash: AccountType)
        else
          ZIO.fail(new IllegalArgumentException(s"Expected 'CASH' found [$s]"))
    }
    final case class Deposit(owner: User) extends AccountType
    object Deposit {
      def make(
          s: String,
          user: User
      ): IO[IllegalArgumentException, AccountType] =
        if (s.toUpperCase == "DEPOSIT") ZIO.succeed(Deposit(user))
        else
          ZIO.fail(
            new IllegalArgumentException(s"Expected 'DEPOSIT' found [$s]")
          )
    }

    def make(
        accountType: String,
        user: Option[User]
    ): IO[IllegalArgumentException, AccountType] =
      (accountType, user) match {
        case (at, Some(user)) => Deposit.make(at, user)
        case (at, None)       => Cash.make(at)
      }
  }

  /** A general class for all types of accounts
    * The owner information will appear only if it is a {{AccountType.Deposit}}
    */
  object AccountId extends Subtype[Long]
  type AccountId = AccountId.Type

  final case class Account(
      number: AccountId,
      balance: BigDecimal,
      accountType: AccountType
  )

  sealed trait TransactionType
  object TransactionType {
    case object Deposit  extends TransactionType
    case object Withdraw extends TransactionType
    case object Book     extends TransactionType

    def asString(tran: TransactionType): String = tran match {
      case Deposit  => "DEPOSIT"
      case Withdraw => "WITHDRAW"
      case Book     => "BOOK"
    }

    def fromString(s: String): TransactionType =
      s.toUpperCase() match {
        case "DEPOSIT"  => Deposit
        case "WITHDRAW" => Withdraw
        case "BOOK"     => Book
        case other      => throw new Exception(s"Expected Deposit/Withdraw/Book, got [$other]")
      }
  }

  object TransactionId extends Subtype[Long]
  type TransactionId = TransactionId.Type

  object Amount extends Subtype[BigDecimal]
  type Amount = Amount.Type

  final case class TransactionData(
      user: UserData,
      accountNumber: AccountId,
      transactionType: TransactionType,
      amount: Amount
  )

  final case class Transaction(
      id: TransactionId,
      accountNumber: AccountId,
      transactionType: TransactionType,
      amount: Amount
  )

  sealed trait TransferType
  object TransferType {
    case object Credit extends TransferType
    case object Debit  extends TransferType
  }

  object PostingId extends Subtype[Long]
  type PostingId = PostingId.Type

  final case class PostingData(
      accountNumber: AccountId,
      amount: Amount,
      transferType: TransferType
  )

  final case class Posting(
      id: PostingId,
      transactionId: TransactionId,
      accountNumber: AccountId,
      amount: Amount,
      transferType: TransferType
  )

  object Posting {
    def fromTransaction(
        tran: Transaction,
        unitCashAccount: Option[AccountId],
        userDepositAccount: Option[AccountId]
    ): Either[Exception, List[PostingData]] =
      tran.transactionType match {
        case TransactionType.Deposit =>
          unitCashAccount
            .map(cashAccount =>
              Right(
                List(
                  PostingData(tran.accountNumber, tran.amount, TransferType.Credit),
                  PostingData(cashAccount, tran.amount, TransferType.Debit)
                )
              )
            )
            .getOrElse(Left(new Exception("Required Unit Cash Account")))

        case TransactionType.Withdraw =>
          unitCashAccount
            .map(cashAccount =>
              Right(
                List(
                  PostingData(tran.accountNumber, Amount(tran.amount * -1), TransferType.Debit),
                  PostingData(cashAccount, Amount(tran.amount * -1), TransferType.Credit)
                )
              )
            )
            .getOrElse(Left(new Exception("Required Unit Cash Account")))

        case TransactionType.Book =>
          userDepositAccount
            .map(userAccount =>
              Right(
                List(
                  PostingData(tran.accountNumber, tran.amount, TransferType.Credit),
                  PostingData(userAccount, Amount(tran.amount * -1), TransferType.Debit)
                )
              )
            )
            .getOrElse(Left(new Exception("Required User Account to perform debit")))
      }

    def from(
        id: PostingId,
        tranId: TransactionId,
        accId: AccountId,
        credit: Option[Amount],
        debit: Option[Amount]
    ): Posting = {
      val (amount, tt) = (credit, debit) match {
        case (Some(c), None) => (c, TransferType.Credit)
        case (None, Some(c)) => (c, TransferType.Debit)
        case _ =>
          throw new Exception(s"Impossible to have both credit/debit or none of them in a posting")
      }
      Posting(id, tranId, accId, amount, tt)
    }

  }

  final case class LedgerLine(
      transactionId: TransactionId,
      transactionType: TransactionType,
      accountId: AccountId,
      credit: Option[Amount],
      debit: Option[Amount]
  )

  object LedgerLine {
    def from(tran: Transaction, posting: Posting): LedgerLine =
      LedgerLine(
        tran.id,
        tran.transactionType,
        posting.accountNumber,
        if (tran.transactionType == TransactionType.Deposit) Some(tran.amount) else None,
        if (tran.transactionType != TransactionType.Deposit) None else Some(tran.amount)
      )
  }

}
