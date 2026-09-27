package filters;

import general.common.Common;
import http.common.Http.Context;
import http.common.Http.Response;
import org.apache.pekko.stream.Materializer;
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
 * A filter that instantiates the HTTP {@link Context} for each incoming request, attaches it
 * to request attributes, and synchronizes response modifications upon completion.
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
        Context context = new Context(requestHeader);
        RequestHeader requestHeaderWithContext = requestHeader.addAttr(Context.CONTEXT_TYPED_KEY, context);

        return nextFilter.apply(requestHeaderWithContext).handle((result, throwable) -> {
            if (throwable != null) {
                throw propagate(throwable);
            }
            return syncResult(result, context);
        });
    }

    private static Result syncResult(Result result, Context context) {
        Response response = context.response();

        // Synchronize headers
        for (Map.Entry<String, String> header : response.headers().entrySet()) {
            result = result.withHeader(header.getKey(), header.getValue());
        }

        // Synchronize cookies
        Collection<Http.Cookie> cookies = response.cookies();
        for (Http.Cookie cookie : cookies) {
            result = result.withCookies(cookie);
        }

        // Synchronize discarding cookies
        Set<String> discardingCookieNames = response.discardingCookieNames();
        for (String cookieName : discardingCookieNames) {
            result = result.discardingCookie(cookieName, Common.getJatosUrlBasePath());
        }

        // Synchronize session and flash
        if (response.isSessionChanged()) {
            result = result.withSession(response.session());
        }
        if (response.isFlashChanged()) {
            result = result.withFlash(response.flash());
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
