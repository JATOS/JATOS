package actions.common

import http.common.Http.Context
import play.api.mvc.RequestHeader

import scala.concurrent.{ExecutionContext, Future}

/**
 * Trait and companion object providing utility methods to execute asynchronous code blocks
 * with an HTTP Context safely bound to the executing thread.
 */
trait ContextRunner {

  /**
   * Runs a block of code asynchronously on the given ExecutionContext with the HTTP Context
   * bound to the worker thread for the duration of the execution.
   *
   * It extracts the Context attached to the request attributes (e.g., by ContextFilter)
   * or creates a fallback Context if none is present (e.g., for WebSockets).
   */
  def withContext[T](request: RequestHeader)(block: => T)(implicit ec: ExecutionContext): Future[T] = {
    ContextRunner.withContext(request)(block)(ec)
  }
}

object ContextRunner extends ContextRunner {

  override def withContext[T](request: RequestHeader)(block: => T)(implicit ec: ExecutionContext): Future[T] = {
    val context = Context.currentOptional(request.asJava)
      .orElseGet(() => new Context(request.asJava))

    Future {
      Context.withContext(context, () => block)
    }(ec)
  }
}
