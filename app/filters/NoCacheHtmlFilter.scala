package filters

import general.common.Common
import http.common.HttpUtils
import org.apache.pekko.stream.Materializer
import play.api.mvc._

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

/**
 * A custom HTML filter designed to prevent caching of GUI HTTP responses by adding
 * specific "no-cache" headers to the response.
 */
@Singleton
class NoCacheHtmlFilter @Inject()(implicit val mat: Materializer, ec: ExecutionContext) extends EssentialFilter {

  override def apply(next: EssentialAction): EssentialAction = EssentialAction { request =>
    next(request).map { result =>
      val shouldApply = HttpUtils.isGuiUrl(request.path) || request.path == Common.getJatosUrlBasePath
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

        result.withHeaders(headersToAdd.toSeq: _*)
      } else {
        result
      }
    }(ec)
  }
}
