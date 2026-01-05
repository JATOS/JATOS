package filters.publix;

import akka.stream.Materializer;
import general.common.Http.Context;
import play.mvc.Filter;
import play.mvc.Http;
import play.mvc.Http.RequestHeader;
import play.mvc.Result;
import services.publix.idcookie.IdCookieAccessor;
import services.publix.idcookie.IdCookieCollection;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import static services.publix.idcookie.IdCookieAccessor.IDCOOKIE_TYPED_KEY;

@Singleton
public class IdCookieFilter extends Filter {

    /**
     * Annotation to mark controller methods that should be processed by the IdCookiesFilter.
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface IdCookies {
    }

    private final IdCookieAccessor idCookieAccessor;

    @Inject
    public IdCookieFilter(Materializer mat, IdCookieAccessor idCookieAccessor) {
        super(mat);
        this.idCookieAccessor = idCookieAccessor;
    }

    @Override
    public CompletionStage<Result> apply(Function<RequestHeader, CompletionStage<Result>> nextFilter,
                                         RequestHeader requestHeader) {
        Optional<IdCookies> idCookiesAnnotation = Context.getAnnotation(requestHeader, IdCookies.class);

        if (!idCookiesAnnotation.isPresent()) {
            return nextFilter.apply(requestHeader);
        }

        return Context.withContext(
                // Logic before the request is handled
                () -> {
                    IdCookieCollection idCookieCollection = idCookieAccessor.extractFromCookies(requestHeader.cookies());
                    Context.current().args().put(IDCOOKIE_TYPED_KEY, idCookieCollection);
                },
                nextFilter.apply(requestHeader)
        ).thenApply(result -> {
            // Logic after the request is handled
            try {
                IdCookieCollection idCookieCollection = Context.current().args().get(IDCOOKIE_TYPED_KEY);
                Http.Cookie[] cookies = idCookieCollection.getAll().stream()
                        .map(idCookieAccessor::generatePlayCookie)
                        .toArray(Http.Cookie[]::new);
                return result.withCookies(cookies);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                // Clean up the thread's Context
                Context.clear();
            }
        });
    }

}
