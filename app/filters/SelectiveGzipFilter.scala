package filters

import javax.inject.{Inject, Singleton}
import org.apache.pekko.stream.Materializer
import play.api.http.HeaderNames.{CONTENT_RANGE, RANGE}
import play.api.http.Status.PARTIAL_CONTENT
import play.filters.gzip.{GzipFilter, GzipFilterConfig}

/** Compress eligible content without changing the representation of byte-range responses. */
@Singleton
class SelectiveGzipFilter @Inject()(config: GzipFilterConfig)(implicit mat: Materializer)
  extends GzipFilter(config.copy(shouldGzip = (request, result) =>
    !request.headers.hasHeader(RANGE) &&
      result.header.status != PARTIAL_CONTENT &&
      !result.header.headers.keys.exists(_.equalsIgnoreCase(CONTENT_RANGE)) &&
      config.shouldGzip(request, result)
  ))
