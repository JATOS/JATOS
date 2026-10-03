package actions.common;

import exceptions.common.JatosException;
import http.common.Http.Context;
import org.junit.After;
import org.junit.Test;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Http.Request;
import play.mvc.Result;
import play.mvc.Results;
import play.test.Helpers;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

@SuppressWarnings({"unchecked"})
public class ContextActionCreatorTest {

    @After
    public void tearDown() {
        Context.clear();
    }

    @Test
    public void createActionBindsContextFromRequestAttributesDuringInvocation() {
        ContextActionCreator actionCreator = new ContextActionCreator();
        Request request = Helpers.fakeRequest("GET", "/test").build();
        Context originalContext = new Context(request);
        Request requestWithContext = request.addAttr(Context.CONTEXT_TYPED_KEY, originalContext);

        AtomicReference<Context> contextInsideAction = new AtomicReference<>();
        Action<Object> action = actionCreator.createAction(requestWithContext, null);
        action.delegate = new Action.Simple() {
            @Override
            public CompletionStage<Result> call(Http.Request req) {
                contextInsideAction.set(Context.current());
                assertSame(requestWithContext, req);
                return CompletableFuture.completedFuture(Results.ok("success"));
            }
        };

        Result result = action.call(requestWithContext).toCompletableFuture().join();
        assertEquals(200, result.status());
        assertSame(originalContext, contextInsideAction.get());
    }

    @Test
    public void missingRequestContextFailsBeforeDelegationWithoutBindingThread() {
        Request request = Helpers.fakeRequest().build();
        Action<Object> action = new ContextActionCreator().createAction(request, null);
        action.delegate = new Action.Simple() {
            @Override public CompletionStage<Result> call(Request req) {
                fail("Missing request context must not reach the controller");
                return CompletableFuture.completedFuture(Results.ok());
            }
        };
        JatosException error = assertThrows(JatosException.class, () -> action.call(request));
        assertEquals("There is no HTTP Context attached to this request.", error.getMessage());
        assertThrows(JatosException.class, Context::current);
    }

    @Test
    public void createActionRestoresPreviousContextAfterSynchronousCallCompletes() {
        ContextActionCreator actionCreator = new ContextActionCreator();
        Request previousRequest = Helpers.fakeRequest("GET", "/previous").build();
        Context previousContext = new Context(previousRequest);
        Context.setCurrent(previousContext);

        Request actionRequest = Helpers.fakeRequest("GET", "/action").build();
        Context actionContext = new Context(actionRequest);
        Request requestWithContext = actionRequest.addAttr(Context.CONTEXT_TYPED_KEY, actionContext);

        Action<Object> action = actionCreator.createAction(requestWithContext, null);
        action.delegate = new Action.Simple() {
            @Override
            public CompletionStage<Result> call(Http.Request req) {
                assertSame(actionContext, Context.current());
                return CompletableFuture.completedFuture(Results.ok("done"));
            }
        };

        action.call(requestWithContext).toCompletableFuture().join();
        assertSame(previousContext, Context.current());
    }

    @Test
    public void createActionRestoresNullContextIfNoPreviousContextExisted() {
        ContextActionCreator actionCreator = new ContextActionCreator();
        Request request = Helpers.fakeRequest("GET", "/test").build();
        Context actionContext = new Context(request);
        Request requestWithContext = request.addAttr(Context.CONTEXT_TYPED_KEY, actionContext);

        Action<Object> action = actionCreator.createAction(requestWithContext, null);
        action.delegate = new Action.Simple() {
            @Override
            public CompletionStage<Result> call(Http.Request req) {
                assertSame(actionContext, Context.current());
                return CompletableFuture.completedFuture(Results.ok("done"));
            }
        };

        action.call(requestWithContext).toCompletableFuture().join();
        try {
            Context.current();
            fail("Expected Context.current() to throw when no context is active");
        } catch (JatosException e) {
            assertEquals("There is no HTTP Context available from here.", e.getMessage());
        }
    }

    @Test
    public void createActionAllowsModifyingContextResponseInAction() {
        ContextActionCreator actionCreator = new ContextActionCreator();
        Request request = Helpers.fakeRequest("GET", "/test").build();
        Context actionContext = new Context(request);
        Request requestWithContext = request.addAttr(Context.CONTEXT_TYPED_KEY, actionContext);

        Action<Object> action = actionCreator.createAction(requestWithContext, null);
        action.delegate = new Action.Simple() {
            @Override
            public CompletionStage<Result> call(Http.Request req) {
                Context.current().response().setHeader("X-Custom", "Custom-Val");
                Context.current().response().putSession("user", "testUser");
                return CompletableFuture.completedFuture(Results.ok("done"));
            }
        };

        action.call(requestWithContext).toCompletableFuture().join();
        assertEquals("Custom-Val", actionContext.response().headers().get("X-Custom"));
        assertEquals("testUser", actionContext.response().session().get("user").orElse(""));
    }
    @Test
    public void missingRequestContextDoesNotUseOrClearAnotherThreadContext() {
        Request request = Helpers.fakeRequest().build();
        Context previous = new Context(Helpers.fakeRequest("GET", "/other").build());
        Context.setCurrent(previous);
        Action<Object> action = new ContextActionCreator().createAction(request, null);
        assertThrows(JatosException.class, () -> action.call(request));
        assertSame(previous, Context.current());
    }

    @Test
    public void synchronousFailureRestoresPreviousContext() {
        Context previous = new Context(Helpers.fakeRequest().build());
        Context.setCurrent(previous);
        Request original = Helpers.fakeRequest("GET", "/failure").build();
        Request request = original.addAttr(Context.CONTEXT_TYPED_KEY, new Context(original));
        Action<Object> action = new ContextActionCreator().createAction(request, null);
        IllegalStateException failure = new IllegalStateException("expected");
        action.delegate = new Action.Simple() {
            @Override
            public CompletionStage<Result> call(Request req) {
                assertNotSame(previous, Context.current());
                throw failure;
            }
        };
        assertSame(failure, assertThrows(IllegalStateException.class, () -> action.call(request)));
        assertSame(previous, Context.current());
    }

}
