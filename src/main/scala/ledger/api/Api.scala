package ledger.api

import akka.event.Logging.DebugLevel
import akka.http.interop._
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Route, _}
import ledger.app.{App, JsonSupport, TransactionRequest}
import ledger.business.error.DomainError
import ledger.business.error.DomainError.{RepositoryError, ValidationError}
import ledger.business.model.UserData
import zio._
import zio.json.ast.Json

trait Api {
  def routes: Route
}

object Api {

  val live: URLayer[Has[App], Has[Api]] =
    ZLayer.fromFunction { app =>
      implicit val domainErrorResponse: ErrorResponse[DomainError] = {
        case RepositoryError(_) => HttpResponse(StatusCodes.InternalServerError)
        case ValidationError(_) => HttpResponse(StatusCodes.BadRequest)
      }

      new Api with JsonSupport with ZIOSupport {
        override def routes: Route = ledgerRoutes

        val ledgerRoutes: Route =
          pathPrefix("/account/put") {
            decodeRequest {
              logRequestResult("account:put", DebugLevel) {
                post {
                  entity(Directives.as[UserData]) { userData =>
                    complete(App.createDepositAccount(userData).provide(app))
                  }
                }
              }
            }
          } ~ pathPrefix("transaction/put") {
            post {
              entity(Directives.as[TransactionRequest]) { tran =>
                complete(App.doTransaction(tran).provide(app).as[Json.Obj](Json.Obj(Chunk.empty)))
              }
            }
          } ~ pathPrefix("balance/get") {
            post {
              entity(Directives.as[UserData]) { tran =>
                complete(App.getBalance(tran).provide(app))
              }
            }
          } ~ pathPrefix("transaction/get") {
            post {
              entity(Directives.as[UserData]) { tran =>
                complete(App.getBalance(tran).provide(app))
              }
            }
          }
      }
    }

}
