package ledger.business

object error {

  sealed trait DomainError

  object DomainError {
    final case class RepositoryError(cause: Exception) extends DomainError
    final case class ValidationError(msg: String) extends DomainError
  }
}
