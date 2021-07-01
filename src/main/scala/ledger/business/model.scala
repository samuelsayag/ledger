package ledger.business

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
    case object Cash extends AccountType
    final case class Deposit(owner: User) extends AccountType
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
    case object Deposit extends TransactionType
    case object Withdraw extends TransactionType
    case object Book extends TransactionType
  }

  final case class TransactionData(
      accountNumber: AccountId,
      transactionType: TransactionType,
      amount: BigDecimal
  )

  object TransactionId extends Subtype[Long]
  type TransactionId = TransactionId.Type

  final case class Transaction(
      id: Long,
      accountNumber: AccountId,
      transactionType: TransactionType,
      amount: BigDecimal
  )

  sealed trait TransferType
  object TransferType {
    case object Credit extends TransferType
    case object Debit extends TransferType
  }

  final case class Posting(
      id: Long,
      transactionId: TransactionId,
      accountNumber: AccountId,
      amount: BigDecimal,
      transferType: TransferType
  )
}