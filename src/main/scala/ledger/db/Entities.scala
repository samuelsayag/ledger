package ledger.db

import ledger.business.model.{AccountId, Amount, PostingId, TransactionId, User, UserId}
import slick.dbio.DBIO
import slick.jdbc.JdbcProfile
import slick.sql.SqlProfile
import slick.lifted.ForeignKeyQuery
import slick.dbio.{DBIOAction, Effect, NoStream}

trait Profile {
  val profile: JdbcProfile
}

trait EntityIdMappers {
  self: Profile =>
  import profile.api._

  implicit def userIdMapper: BaseColumnType[UserId] =
    MappedColumnType.base[UserId, Long](
      ent => ent,
      value => UserId(value)
    )

  implicit def accountIdMapper: BaseColumnType[AccountId] =
    MappedColumnType.base[AccountId, Long](
      ent => ent,
      value => AccountId(value)
    )

  implicit def transactionIdMapper: BaseColumnType[TransactionId] =
    MappedColumnType.base[TransactionId, Long](
      ent => ent,
      value => TransactionId(value)
    )

  implicit def amountMapper: BaseColumnType[Amount] =
    MappedColumnType.base[Amount, BigDecimal](
      ent => ent,
      value => Amount(value)
    )

  implicit def postingIdMapper: BaseColumnType[PostingId] =
    MappedColumnType.base[PostingId, Long](
      ent => ent,
      value => PostingId(value)
    )
}

trait DBIOHelper {

  implicit class Either2DBIO[B](either: Either[Throwable, B]) {
    def toDBIO: DBIOAction[B, NoStream, Effect] = either match {
      case Right(b) => DBIO.successful(b)
      case Left(th) => DBIO.failed(th)
    }
  }
}

trait Entities extends EntityIdMappers {
  self: Profile =>

  import profile.api._
  import SqlProfile.ColumnOption._

  class Users(tag: Tag) extends Table[User](tag, "users") {
    def id: Rep[UserId]   = column[UserId]("id", O.PrimaryKey, O.AutoInc)
    def name: Rep[String] = column[String]("name", NotNull)
    def *                 = (id, name).<>((User.apply _).tupled, User.unapply)
  }

  val users: TableQuery[Users] = TableQuery[self.Users]

  class Accounts(tag: Tag)
      extends Table[(AccountId, Amount, String, Option[UserId])](tag, "accounts") {
    def id: Rep[AccountId]       = column[AccountId]("id", O.PrimaryKey, O.AutoInc)
    def balance: Rep[Amount]     = column[Amount]("balance", NotNull)
    def accountType: Rep[String] = column[String]("accountType", NotNull)
    def userId: Rep[Option[ledger.business.model.UserId]] =
      column[Option[UserId]]("userId", Nullable)
    def * = (id, balance, accountType, userId)
    def accountUsers: ForeignKeyQuery[Users, User] =
      foreignKey("ACC_USER_FK", userId, users)(
        _.id.?,
        onUpdate = ForeignKeyAction.Restrict,
        onDelete = ForeignKeyAction.Cascade
      )
  }

  val accounts: TableQuery[Accounts] = TableQuery[self.Accounts]

  class Transactions(tag: Tag)
      extends Table[(TransactionId, AccountId, String, Amount)](tag, "transactions") {
    def id: Rep[TransactionId] =
      column[TransactionId]("id", O.PrimaryKey, O.AutoInc)
    def accountId: Rep[AccountId] = column[AccountId]("accountId", NotNull)
    def transactionType: Rep[String] =
      column[String]("transactionType", NotNull)
    def amount: Rep[Amount] = column[Amount]("amount", NotNull)
    def *                   = (id, accountId, transactionType, amount)
    def tranAccounts: ForeignKeyQuery[Accounts, (AccountId, Amount, String, Option[UserId])] =
      foreignKey("TRAN_ACC_FK", accountId, accounts)(
        _.id,
        onUpdate = ForeignKeyAction.Restrict,
        onDelete = ForeignKeyAction.Cascade
      )

  }

  val transactions: TableQuery[Transactions] = TableQuery[self.Transactions]

  class Postings(tag: Tag)
      extends Table[(PostingId, TransactionId, AccountId, Option[Amount], Option[Amount])](
        tag,
        "postings"
      ) {
    def id: Rep[ledger.business.model.PostingId] = column[PostingId]("id", O.PrimaryKey, O.AutoInc)
    def transactionId: Rep[ledger.business.model.TransactionId] =
      column[TransactionId]("transactionId", NotNull)
    def accountId: Rep[ledger.business.model.AccountId] = column[AccountId]("accountId", NotNull)
    def credit: Rep[Option[ledger.business.model.Amount]] =
      column[Option[Amount]]("credit", Nullable)
    def debit: Rep[Option[ledger.business.model.Amount]] = column[Option[Amount]]("debit", Nullable)
    def *                                                = (id, transactionId, accountId, credit, debit)
    def postingTransaction: ForeignKeyQuery[
      Transactions,
      (TransactionId, AccountId, String, Amount)
    ] =
      foreignKey("POST_TRAN_FK", transactionId, transactions)(
        _.id,
        onUpdate = ForeignKeyAction.Restrict,
        onDelete = ForeignKeyAction.Cascade
      )

    def postingAcc: ForeignKeyQuery[Accounts, (AccountId, Amount, String, Option[UserId])] =
      foreignKey("POST_ACC_FK", accountId, accounts)(
        _.id,
        onUpdate = ForeignKeyAction.Restrict,
        onDelete = ForeignKeyAction.Cascade
      )
  }

  val postings: TableQuery[Postings] = TableQuery[self.Postings]
}
