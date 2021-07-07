package ledger

import zio.json._
import de.heikoseeberger.akkahttpziojson.ZioJsonSupport
import ledger.business.model.{AccountId, Amount, UserData}

package object app {

  sealed trait TransactionRequest
  object TransactionRequest {
    final case class Deposit(name: String, amount: Amount)  extends TransactionRequest
    final case class Withdraw(name: String, amount: Amount) extends TransactionRequest
    final case class Book(name: String, amount: Amount, accountId: AccountId)
        extends TransactionRequest

    implicit val codec: JsonCodec[TransactionRequest] = DeriveJsonCodec.gen[TransactionRequest]
  }

  trait JsonSupport extends ZioJsonSupport {
    implicit val userDataCodec: JsonCodec[UserData]              = DeriveJsonCodec.gen[UserData]
    implicit val transactionCodec: JsonCodec[TransactionRequest] = TransactionRequest.codec
  }

}
