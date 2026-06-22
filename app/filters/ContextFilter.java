package filters;

import akka.stream.Materializer;
import http.common.Http.Context;
import play.mvc.Filter;
import play.mvc.Http;
import play.mvc.Http.RequestHeader;
import play.mvc.Result;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * A filter that sets up and cleans up an HTTP context for each request.
 *
 * This filter ensures that a thread-local {@link Context} is established at the start of a request and properly cleaned
 * up at the end, preventing potential memory leaks or incorrect state propagation across asynchronous operations.
 *
 * The filter intercepts the HTTP request/response lifecycle, creating an instance of {@link Context} for the request
 * and updating the thread-local context state as follows:
 *
 * 1. Initializes the {@link Context} at the start of the request and binds it to the current thread. 2. Ensures the
 * {@link Context} is restored for any asynchronous operations during request processing. 3. Synchronizes headers,
 * cookies, session, and flash scope from the {@link Context} into the final {@link Result}. 4. Properly clears the
 * {@link Context} after the request is completed to avoid memory leaks.
 *
 * The filter also handles exceptions that may occur during request processing by propagating them correctly, ensuring
 * proper exception chaining where applicable.
 */
@Singleton
public class ContextFilter extends Filter {

    @Inject
    public ContextFilter(Materializer mat) {
        super(mat);
    }

    @Override
    public CompletionStage<Result> apply(Function<RequestHeader, CompletionStage<Result>> nextFilter,
                                         Http.RequestHeader requestHeader) {
        // Initialize Context for the start of the request
        Context context = new Context(requestHeader);
        Context.setCurrent(context);

        return nextFilter.apply(requestHeader)
                .handle((result, throwable) -> {
                    Context.setCurrent(context);
                    try {
                        if (throwable != null) {
                            throw propagate(throwable);
                        }

                        result = syncHeaders(result);
                        result = syncCookies(result);
                        result = syncSession(result);
                        result = syncFlash(result);

                        return result;
                    } finally {
                        // Final cleanup to prevent memory leaks in the thread pool
                        Context.clear();
                    }
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
