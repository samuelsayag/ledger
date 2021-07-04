package ledger.business

import ledger.business.error.DomainError
import zio._
import zio.prelude._

package object model {

  // TODO - What is the relation of an account with a currency?

  /** - id: Could have restriction on the format (create new type for that)
    * - name: Could have restriction on the format (create new type for that)
    */
  object UserId extends Subtype[Long]
  type UserId = UserId.Type

  final case class User(id: UserId, name: String)

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
  }

  object TransactionId extends Subtype[Long]
  type TransactionId = TransactionId.Type

  object Amount extends Subtype[BigDecimal]
  type Amount = Amount.Type

  final case class TransactionData(
      user: User,
      accountNumber: AccountId,
      transactionType: TransactionType,
      amount: BigDecimal
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
    ): IO[DomainError, List[PostingData]] = {
      tran.transactionType match {
        case TransactionType.Deposit =>
          unitCashAccount
            .map(cashAccount =>
              ZIO.succeed(
                List(
                  PostingData(tran.accountNumber, tran.amount, TransferType.Credit),
                  PostingData(cashAccount, tran.amount, TransferType.Debit)
                )
              )
            )
            .getOrElse(
              ZIO.fail(
                DomainError.ValidationError("Required Unit Cash Account")
              )
            )

        case TransactionType.Withdraw =>
          unitCashAccount
            .map(cashAccount =>
              ZIO.succeed(
                List(
                  PostingData(tran.accountNumber, tran.amount, TransferType.Debit),
                  PostingData(cashAccount, tran.amount, TransferType.Credit)
                )
              )
            )
            .getOrElse(
              ZIO.fail(
                DomainError.ValidationError("Required Unit Cash Account")
              )
            )

        case TransactionType.Book =>
          userDepositAccount
            .map(userAccount =>
              ZIO.succeed(
                List(
                  PostingData(tran.accountNumber, tran.amount, TransferType.Credit),
                  PostingData(userAccount, tran.amount, TransferType.Debit)
                )
              )
            )
            .getOrElse(
              ZIO.fail(
                DomainError.ValidationError("Required User Account to perform debit")
              )
            )
      }
    }
  }
}
