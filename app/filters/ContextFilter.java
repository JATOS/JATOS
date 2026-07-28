package filters;

import akka.stream.Materializer;
import general.common.Common;
import http.common.Http.Context;
import play.mvc.Filter;
import play.mvc.Http;
import play.mvc.Http.RequestHeader;
import play.mvc.Result;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * @formatter:off
 * A filter that creates and finalizes an HTTP {@link Context} for each request.
 *
 * The {@link Context} is stored in two places, each with a different purpose:
 *
 * 1. Request attribute:
 *    The {@link Context} is attached to the current {@link RequestHeader} via {@link Context#CONTEXT_TYPED_KEY}.
 *    This is the durable, request-scoped carrier of the context. It survives Play's asynchronous request
 *    processing and possible thread switches because it is stored on the request, not on a Java thread.
 *
 * 2. Thread-local binding:
 *    The same {@link Context} is temporarily bound to the current thread via {@link Context#setCurrent(Context)}
 *    whenever synchronous request code needs to use {@link Context#current()}. This binding is deliberately
 *    short-lived and must be cleared or restored after the synchronous block finishes, because Play reuses
 *    threads between requests.
 *
 * Important:
 * - The request attribute is the source of truth.
 * - The thread-local binding is deliberately short-lived.
 * - Response syncing applies to normal HTTP Results but not WebSockets
 *
 * The request lifecycle is handled by this filter:
 * 1. Create a new {@link Context} for the incoming request.
 * 2. Attach the {@link Context} to the {@link RequestHeader} using {@link Context#CONTEXT_TYPED_KEY}.
 * 3. Temporarily bind the {@link Context} to the current thread while invoking the next filter.
 * 4. Clear the thread-local binding immediately after the synchronous invocation of the next filter returns.
 * 5. When the resulting {@link CompletionStage} completes, temporarily rebind the same {@link Context}.
 * 6. Synchronize headers, cookies, session, and flash scope from the {@link Context} into the final {@link Result}.
 * 7. Restore or clear the previous thread-local binding to prevent context leakage between reused threads.
 *
 * Other actions or filters that may run on a different thread should retrieve the context from
 * {@link RequestHeader#attrs()} or {@link play.mvc.Http.Request#attrs()} using {@link Context#CONTEXT_TYPED_KEY}, then
 * temporarily bind it with {@link Context#withContext(Context, java.util.function.Supplier)} before executing code
 * that relies on {@link Context#current()}.
 *
 * @formatter:on
 */
@Singleton
public class ContextFilter extends Filter {

    @Inject
    public ContextFilter(Materializer mat) {
        super(mat);
    }

    @Override
    public CompletionStage<Result> apply(Function<RequestHeader, CompletionStage<Result>> nextFilter, Http.RequestHeader requestHeader) {
        Context context = new Context(requestHeader);
        // Attach the context to the request passed down the Play pipeline.
        RequestHeader requestHeaderWithContext = requestHeader.addAttr(Context.CONTEXT_TYPED_KEY, context);

        CompletionStage<Result> resultStage;
        try {
            Context.setCurrent(context);
            resultStage = nextFilter.apply(requestHeaderWithContext);
        } finally {
            Context.clear();
        }

        return resultStage.handle((result, throwable) -> {
            Context finalContext = requestHeaderWithContext.attrs().get(Context.CONTEXT_TYPED_KEY);
            return Context.withContext(finalContext, () -> {
                if (throwable != null) {
                    throw propagate(throwable);
                }

                Result syncedResult = syncHeaders(result);
                syncedResult = syncCookies(syncedResult);
                syncedResult = syncSession(syncedResult);
                syncedResult = syncFlash(syncedResult);

                return syncedResult;
            });
        });
    }

    private static Result syncHeaders(Result result) {
        for (Map.Entry<String, String> header : Context.current().response().headers().entrySet()) {
            result = result.withHeader(header.getKey(), header.getValue());
        }
        return result;
    }

    private static Result syncCookies(Result result) {
        Collection<Http.Cookie> cookies = Context.current().response().cookies();
        for (Http.Cookie cookie : cookies) {
            result = result.withCookies(cookie);
        }

        // Handle discarding cookies
        Set<String> discardingCookieNames = Context.current().response().discardingCookieNames();
        for (String cookieName : discardingCookieNames) {
            result = result.discardingCookie(cookieName, Common.getJatosUrlBasePath());
        }

        return result;
    }

    private static Result syncSession(Result result) {
        if (Context.current().response().isSessionChanged()) {
            result = result.withSession(Context.current().response().session());
        }
        return result;
    }

    private static Result syncFlash(Result result) {
        if (Context.current().response().isFlashChanged()) {
            result = result.withFlash(Context.current().response().flash());
        }
        return result;
    }

    private static RuntimeException propagate(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            throwable = throwable.getCause();
        }

        if (throwable instanceof RuntimeException) {
            return (RuntimeException) throwable;
        }

        if (throwable instanceof Error) {
            throw (Error) throwable;
        }

        return new CompletionException(throwable);
    }

}
