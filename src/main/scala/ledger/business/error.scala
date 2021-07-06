package ledger.business

object error {

  sealed trait DomainError

  object DomainError {
    def fromThrowable(th: Throwable): DomainError = RepositoryError(new Exception(th))
    def fromMsg(msg: String): DomainError         = RepositoryError(new Exception(msg))

    final case class RepositoryError(cause: Exception) extends DomainError
    final case class ValidationError(msg: String)      extends DomainError
  }
}
