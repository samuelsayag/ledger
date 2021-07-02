package ledger.db

import ledger.business.model.User
import ledger.business.model.UserId
import slick.jdbc.JdbcProfile
import ledger.business.model.AccountId
import slick.sql.SqlProfile
import slick.lifted.ForeignKeyQuery

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
}

trait Entities extends EntityIdMappers {
  self: Profile =>
  import profile.api._
  import SqlProfile.ColumnOption._

  class Users(tag: Tag) extends Table[User](tag, "users") {
    def id: Rep[UserId] = column[UserId]("id", O.PrimaryKey, O.AutoInc)
    def name: Rep[String] = column[String]("name")
    def * = (id, name).<>((User.apply _).tupled, User.unapply)
  }

  val users: TableQuery[Users] = TableQuery[self.Users]

  class Accounts(tag: Tag)
      extends Table[(AccountId, BigDecimal, String, UserId)](tag, "accounts") {
    def id: Rep[AccountId] = column[AccountId]("id", O.PrimaryKey, O.AutoInc)
    def balance: Rep[BigDecimal] = column[BigDecimal]("balance")
    def accountType: Rep[String] = column[String]("accountType")
    def userId: Rep[UserId] = column[UserId]("userId", Nullable)
    def * = (id, balance, accountType, userId)
    def accountUsers: ForeignKeyQuery[Users, User] =
      foreignKey("ACC_USER_FK", userId, users)(
        _.id,
        onUpdate = ForeignKeyAction.Restrict,
        onDelete = ForeignKeyAction.Cascade
      )
  }

  val accounts: TableQuery[Accounts] = TableQuery[self.Accounts]

  class Transactions(tag: Tag)
      extends Table[(Long, AccountId, String, BigDecimal)](
        tag,
        "transactions"
      ) {
    def id: Rep[Long] = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def accountId: Rep[AccountId] = column[AccountId]("accountId")
    def transactionType: Rep[String] = column[String]("transactionType")
    def amount: Rep[BigDecimal] = column[BigDecimal]("amount", Nullable)
    def * = (id, accountId, transactionType, amount)
    def tranAccounts
        : ForeignKeyQuery[Accounts, (AccountId, BigDecimal, String, UserId)] =
      foreignKey("TRAN_ACC_FK", accountId, accounts)(
        _.id,
        onUpdate = ForeignKeyAction.Restrict,
        onDelete = ForeignKeyAction.NoAction
      )

  }

  val transactions: TableQuery[Transactions] = TableQuery[self.Transactions]
}
