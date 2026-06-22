package filters

import akka.stream.Materializer
import http.common.Http.Context
import play.api.mvc._

import java.util.function.Supplier
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

/**
 * A custom HTML filter designed to prevent caching of GUI HTTP responses by adding
 * specific "no-cache" headers to the response.
 */
@Singleton
class NoCacheHtmlFilter @Inject()(implicit val mat: Materializer, ec: ExecutionContext) extends EssentialFilter {

  override def apply(next: EssentialAction): EssentialAction = EssentialAction { request =>
    val context = Context.current()

    next(request).map { result =>
      Context.withContext(context, new Supplier[Result] {
        override def get(): Result = {
          val shouldApply = request.path.startsWith("/jatos")
          val contentType = result.body.contentType.getOrElse("")

          if (shouldApply && contentType.startsWith("text/html")) {
            val existingHeaders = result.header.headers
            val noCacheHeaders = Map(
              "Cache-Control" -> "no-cache, no-store, must-revalidate",
              "Pragma" -> "no-cache",
              "Expires" -> "0"
            )

            val headersToAdd = noCacheHeaders.filterNot { case (name, _) =>
              existingHeaders.keys.exists(_.equalsIgnoreCase(name))
            }

            headersToAdd.foreach { case (name, value) =>
              Context.current().response().setHeader(name, value)
            }
          }

          result
        }
      })
    }(ec)
  }
}