package actions.common

import exceptions.common.JatosException
import http.common.Http.Context
import org.junit.Assert._
import org.junit.{After, Test}
import play.api.mvc.RequestHeader
import play.api.test.FakeRequest

import java.util.concurrent.{CompletableFuture, Executors}
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

class ContextRunnerTest extends ContextRunner {

  private val testExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(2))

  @After
  def tearDown(): Unit = {
    Context.clear()
  }

  @Test
  def withContextBindsContextFromRequestAttributes(): Unit = {
    val fakeRequest: RequestHeader = FakeRequest("GET", "/test")
    val context = new Context(fakeRequest.asJava)
    val requestWithContext = fakeRequest.asJava.addAttr(Context.CONTEXT_TYPED_KEY, context).asScala

    val future = withContext(requestWithContext) {
      val current = Context.current()
      assertSame(context, current)
      "ok"
    }(testExecutor)

    val result = Await.result(future, 3.seconds)
    assertEquals("ok", result)
  }

  @Test
  def withContextCreatesFallbackWhenNoContextInAttributes(): Unit = {
    val fakeRequest: RequestHeader = FakeRequest("GET", "/test")

    val future = withContext(fakeRequest) {
      val current = Context.current()
      assertNotNull(current)
      assertEquals("/test", current.requestHeader().path())
      assertEquals("GET", current.requestHeader().method())
      "fallback"
    }(testExecutor)

    val result = Await.result(future, 3.seconds)
    assertEquals("fallback", result)
  }

  @Test
  def withContextCleansUpContextOnExecutingThread(): Unit = {
    val fakeRequest: RequestHeader = FakeRequest("GET", "/test")
    val singleThreadEc = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())

    val future = withContext(fakeRequest) {
      Context.current()
      "done"
    }(singleThreadEc)

    Await.result(future, 3.seconds)

    val checkFuture = new CompletableFuture[Boolean]()
    singleThreadEc.execute(new Runnable {
      override def run(): Unit = {
        try {
          Context.current()
          checkFuture.complete(false)
        } catch {
          case _: JatosException =>
            checkFuture.complete(true)
        }
      }
    })

    assertTrue(checkFuture.get())
  }

  @Test
  def companionObjectWithContextWorksDirectly(): Unit = {
    val fakeRequest: RequestHeader = FakeRequest("GET", "/test")

    val future = ContextRunner.withContext(fakeRequest) {
      assertNotNull(Context.current())
      "object-ok"
    }(testExecutor)

    val result = Await.result(future, 3.seconds)
    assertEquals("object-ok", result)
  }
}
