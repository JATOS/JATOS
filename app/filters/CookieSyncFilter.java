package filters;

import akka.stream.Materializer;
import general.common.Http.Context;
import play.mvc.Filter;
import play.mvc.Http;
import play.mvc.Http.RequestHeader;
import play.mvc.Result;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collection;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

@Singleton
public class CookieSyncFilter extends Filter {

    @Inject
    public CookieSyncFilter(Materializer mat) {
        super(mat);
    }

    @Override
    public CompletionStage<Result> apply(Function<RequestHeader, CompletionStage<Result>> nextFilter,
                                         Http.RequestHeader requestHeader) {
        return Context.withContext(nextFilter.apply(requestHeader))
                .thenApply(result -> {
                    try {
                        Collection<Http.Cookie> cookies = Context.current().response().cookies();
                        for (Http.Cookie cookie : cookies) {
                            result = result.withCookies(cookie);
                        }
                        return result;
                    } finally {
                        // Clean up the thread's Context
                        Context.clear();
                    }
                });
    }

}
