package filters

import akka.stream.Materializer
import javax.inject.{Inject, Singleton}
import play.api.mvc._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NoCacheHtmlFilter @Inject()(implicit val mat: Materializer, ec: ExecutionContext) extends EssentialFilter {

  override def apply(next: EssentialAction): EssentialAction = EssentialAction { request =>
    next(request).map { result =>
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

        if (headersToAdd.nonEmpty) result.withHeaders(headersToAdd.toSeq: _*) else result
      } else {
        result
      }
    }(ec)
  }
}