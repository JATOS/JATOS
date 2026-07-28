package filters.publix;

import akka.stream.Materializer;
import http.common.Http;
import http.common.Http.Context;
import http.common.RouteAnnotations;
import play.libs.typedmap.TypedKey;
import play.mvc.Filter;
import play.mvc.Http.RequestHeader;
import play.mvc.Result;
import services.publix.idcookie.IdCookieCollection;
import services.publix.idcookie.IdCookieService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * A Play filter that extracts the ID cookies from the requests and puts them into {@link Http.Args} for using during
 * request's logic. New/removed/updated cookies must be written to {@link Http.Response} - they won't be automatically
 * written back to the response.
 */
@Singleton
public class IdCookieFilter extends Filter {

    public static final TypedKey<IdCookieCollection> IDCOOKIES_TYPED_KEY = TypedKey.create("idCookies");

    /**
     * Annotation to mark controller methods that should be processed by the IdCookiesFilter.
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface IdCookies {
    }

    private final IdCookieService idCookieService;

    @Inject
    public IdCookieFilter(Materializer mat, IdCookieService idCookieService) {
        super(mat);
        this.idCookieService = idCookieService;
    }

    @Override
    public CompletionStage<Result> apply(Function<RequestHeader, CompletionStage<Result>> nextFilter,
                                         RequestHeader requestHeader) {
        // Check for this request if the controller class or method is annotated with @IdCookies
        Optional<IdCookies> idCookiesAnnotation = RouteAnnotations.get(requestHeader, IdCookies.class);

        if (idCookiesAnnotation.isEmpty()) {
            return nextFilter.apply(requestHeader);
        }

        Context context = requestHeader.attrs().get(Context.CONTEXT_TYPED_KEY);

        return Context.withContext(context, () -> {
            IdCookieCollection cookies = idCookieService.extractFromCookies(requestHeader.cookies());
            Context.current().args().put(IDCOOKIES_TYPED_KEY, cookies);
            return nextFilter.apply(requestHeader);
        });
    }

}
