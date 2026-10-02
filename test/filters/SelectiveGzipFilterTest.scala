package filters

import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import java.nio.charset.StandardCharsets.UTF_8
import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.junit.{After, Test}
import org.junit.Assert._
import play.api.Configuration
import play.api.libs.streams.Accumulator
import play.api.mvc.{EssentialAction, Result, Results}
import play.api.test.FakeRequest
import play.filters.gzip.GzipFilterConfig

import scala.concurrent.Await
import scala.concurrent.duration._

class SelectiveGzipFilterTest {
  private val system = ActorSystem("selective-gzip-test")
  private implicit val mat: Materializer = Materializer(system)
  private val configuration = Configuration(ConfigFactory.load("application.conf"))
  private val filter = new SelectiveGzipFilter(GzipFilterConfig.fromConfiguration(configuration))
  private val payload = "Study result data with Unicode: äöü.\n" * 200

  @After
  def stop(): Unit = Await.result(system.terminate(), 10.seconds)

  private def run(result: Result, headers: (String, String)*): Result = {
    val request = FakeRequest("GET", "/test").withHeaders(headers: _*)
    Await.result(filter(EssentialAction(_ => Accumulator.done(result)))(request).run(), 10.seconds)
  }

  private def bytes(result: Result): Array[Byte] =
    Await.result(result.body.consumeData, 10.seconds).toArray

  private def assertCompressed(result: Result): Unit = {
    assertEquals(Some("gzip"), result.header.headers.get("Content-Encoding"))
    val gzip = new GZIPInputStream(new ByteArrayInputStream(bytes(result)))
    try assertEquals(payload, new String(gzip.readAllBytes(), UTF_8))
    finally gzip.close()
  }

  @Test
  def compressesConfiguredTextTypesLosslessly(): Unit = {
    assertTrue(configuration.get[Seq[String]]("play.filters.enabled").contains("filters.SelectiveGzipFilter"))
    assertFalse(configuration.get[Seq[String]]("play.filters.enabled").contains("play.filters.gzip.GzipFilter"))
    Seq("text/html", "text/css", "application/javascript", "application/json", "application/x-ndjson",
      "text/plain; charset=utf-8", "text/csv", "image/svg+xml").foreach { contentType =>
      assertCompressed(run(Results.Ok(payload).as(contentType), "Accept-Encoding" -> "gzip"))
    }
  }

  @Test
  def preservesChunkedExportContents(): Unit = {
    val chunks = Source(payload.grouped(37).map(ByteString(_)).toList)
    assertCompressed(run(Results.Ok.chunked(chunks).as("text/plain"), "Accept-Encoding" -> "gzip"))
  }

  @Test
  def leavesRangeResponsesUncompressed(): Unit = {
    val response = Results.Ok(payload).as("text/plain")
    Seq(
      run(response, "Accept-Encoding" -> "gzip", "Range" -> "bytes=0-99"),
      run(Results.Status(206)(payload).as("text/plain"), "Accept-Encoding" -> "gzip"),
      run(response.withHeaders("content-range" -> "bytes 0-99/1000"), "Accept-Encoding" -> "gzip")
    ).foreach { result =>
      assertFalse(result.header.headers.contains("Content-Encoding"))
      assertArrayEquals(payload.getBytes(UTF_8), bytes(result))
    }
  }

  @Test
  def leavesArchivesAndBinaryMediaUncompressed(): Unit = {
    Seq("application/zip", "application/octet-stream", "image/png", "image/jpeg", "audio/mpeg", "video/mp4")
      .foreach { contentType =>
        val result = run(Results.Ok(payload).as(contentType), "Accept-Encoding" -> "gzip")
        assertFalse(contentType, result.header.headers.contains("Content-Encoding"))
        assertArrayEquals(payload.getBytes(UTF_8), bytes(result))
      }
  }

  @Test
  def respectsNegotiationAndMinimumSize(): Unit = {
    val response = Results.Ok(payload).as("text/plain")
    Seq(run(response), run(response, "Accept-Encoding" -> "gzip;q=0"),
      run(Results.Ok("small").as("text/plain"), "Accept-Encoding" -> "gzip")).foreach { result =>
      assertFalse(result.header.headers.contains("Content-Encoding"))
    }
  }
}
