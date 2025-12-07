package filters;

import akka.stream.Materializer;
import play.mvc.Filter;
import play.mvc.Http;
import play.mvc.Http.RequestHeader;
import play.mvc.Result;
import services.publix.idcookie.IdCookieModel;
import services.publix.idcookie.IdCookieService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Iterator;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

@Singleton
public class IdCookieFilter extends Filter {

    private final IdCookieService idCookieService;

    @Inject
    public IdCookieFilter(Materializer mat, IdCookieService idCookieService) {
        super(mat);
        this.idCookieService = idCookieService;
    }

    @Override
    public CompletionStage<Result> apply(Function<RequestHeader, CompletionStage<Result>> nextFilter,
                                         Http.RequestHeader requestHeader) {
        return nextFilter
                .apply(requestHeader)
                .thenApply(result -> discardIdCookies(result).withCookies(idCookieService.getIdCookiesAsPlayCookies(requestHeader)));
    }

    private static Result discardIdCookies(Result result) {
        Iterator<Http.Cookie> cookieIterator = result.cookies().iterator();
        while (cookieIterator.hasNext()) {
            Http.Cookie cookie = cookieIterator.next();
            if (cookie.name().startsWith(IdCookieModel.ID_COOKIE_NAME)) {
                cookieIterator.remove();
            }
        }
        return result;
    }

}
