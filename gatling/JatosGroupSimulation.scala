
import scala.concurrent.duration._

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.jdbc.Predef._

import java.net.URLDecoder

class JatosGroupSimulation extends Simulation {

  val httpProtocol = http
    .baseUrl("https://www.example.com")
    .inferHtmlResources()
    .acceptHeader("*/*")
    .acceptEncodingHeader("gzip, deflate")
    .acceptLanguageHeader("en-US,en;q=0.5")
    .doNotTrackHeader("1")
    .userAgentHeader("Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:69.0) Gecko/20100101 Firefox/69.0")

  val headers_0 = Map(
    "Accept" -> "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Upgrade-Insecure-Requests" -> "1")

  val headers_2 = Map("Accept" -> "image/webp,*/*")

  val headers_3 = Map(
    "Accept" -> "text/javascript, application/javascript, application/ecmascript, application/x-ecmascript, */*; q=0.01",
    "X-Requested-With" -> "XMLHttpRequest")

  val headers_7 = Map(
    "Accept" -> "application/json, text/javascript, */*; q=0.01",
    "X-Requested-With" -> "XMLHttpRequest")

  val headers_8 = Map("Content-Type" -> "text/plain")

  val headers_10 = Map(
    "Content-Type" -> "text/plain; charset=UTF-8",
    "X-Requested-With" -> "XMLHttpRequest")

  val headers_11 = Map("X-Requested-With" -> "XMLHttpRequest")


  val scn = scenario("JatosGroupSimulation")
    .exec(
      http("Start").get("/publix/7/start?batchId=40&generalMultiple").check(bodyString.saveAs("BODY")).headers(headers_0)
    ).exec(getCookieValue(CookieKey("JATOS_IDS_0"))
  ).exec(session => {
    val cookie = session("JATOS_IDS_0").as[String]
    val cookieParas = parseUrlParameters(cookie)
    val studyResultId = cookieParas("studyResultId")
    println(s"JATOS_IDS_0: $studyResultId")
    session.set("studyResultId", studyResultId)
  }).exec(
    http("Get init data").get("/publix/7/14/initData?srid=${studyResultId}").headers(headers_7)
  ).exec(
    http("Heartbeat").post("/publix/7/heartbeat?srid=${studyResultId}").headers(headers_8)
  ).exec(
    ws("Join group").connect("wss://www.example.com/publix/7/group/join?srid=${studyResultId}")
  ).pause(5 seconds).exec(
    http("Reassign group").get("/publix/7/group/reassign?srid=${studyResultId}").headers(headers_7)
  ).pause(5 seconds).exec(
    http("Leave group").get("/publix/7/group/leave?srid=${studyResultId}").headers(headers_7)
  ).pause(5 seconds).exec(
    http("Post study session data").post("/publix/7/studySessionData?srid=${studyResultId}").headers(headers_10).body(StringBody("""{"foo":"bar"}"""))
  ).exec(
    http("Finish study").get("/publix/7/end?srid=${studyResultId}").headers(headers_11)
  )

  def parseUrlParameters(url: String) = {
    url.split("&").map(v => {
      val m = v.split("=", 2).map(s => URLDecoder.decode(s, "UTF-8"))
      m(0) -> m(1)
    }).toMap
  }

  //	setUp(scn.inject(atOnceUsers(50))).protocols(httpProtocol)
  setUp(scn.inject(rampUsersPerSec(1) to (1) during (6000 seconds))).protocols(httpProtocol)
}
