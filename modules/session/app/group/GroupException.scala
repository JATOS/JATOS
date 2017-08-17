package group

class GroupException(message: String = null, cause: Throwable = null)
  extends Exception(GroupException.defaultMessage(message, cause), cause)

object GroupException {
  def defaultMessage(message: String, cause: Throwable) =
    if (message != null) message
    else if (cause != null) cause.toString()
    else null
}

