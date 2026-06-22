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
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * A Play filter that extracts the ID cookies from the requests and puts them into {@link Http.Args} where
 * they can be updated by the request's logic. At the end of the request the ID cookies are written back to the
 * response.
 */
@Singleton
public class IdCookieFilter extends Filter {

    public static final TypedKey<Set<String>> INCOMING_IDCOOKIE_NAMES_TYPED_KEY = TypedKey.create("incomingIdCookieNames");
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

        return Context.withContext(
                () -> initFromRequestCookies(requestHeader.cookies()),
                nextFilter.apply(requestHeader)
        ).thenApply(result -> {
            syncIdCookiesToResponse();
            return result;
        });
    }

    /**
     * Initializes the {@link Http.Context#args()} with ID cookies
     */
    public void initFromRequestCookies(play.mvc.Http.Cookies cookies) {
        Http.Context.current().args().put(INCOMING_IDCOOKIE_NAMES_TYPED_KEY, idCookieService.extractIdCookieNames(cookies));
        Http.Context.current().args().put(IDCOOKIES_TYPED_KEY, idCookieService.extractFromCookies(cookies));
    }

    /**
     * Synchronizes the ID cookies from {@link Http.Context#args()} with ID cookies in the response. ID cookies that
     * were removed from {@link Http.Context#args()} during request handling are added as discard cookies.
     */
    public void syncIdCookiesToResponse() {
        Set<String> incomingCookieNames = Http.Context.current().args().get(INCOMING_IDCOOKIE_NAMES_TYPED_KEY);
        Set<String> finalCookieNames = idCookieService.generatePlayCookieNames();

        Set<String> removedCookieNames = new HashSet<>(incomingCookieNames);
        removedCookieNames.removeAll(finalCookieNames);

        Http.Context.current().response().setCookies(idCookieService.generatePlayCookies());
        Http.Context.current().response().setCookies(idCookieService.generateDiscardCookies(removedCookieNames));
    }

}
