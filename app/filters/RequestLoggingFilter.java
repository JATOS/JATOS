package filters;

import akka.stream.Materializer;
import http.common.Http.Context;
import models.common.User;
import play.Logger;
import play.Logger.ALogger;
import play.mvc.Filter;
import play.mvc.Http;
import play.mvc.Result;
import utils.common.StringUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import static auth.gui.AuthAction.SIGNEDIN_USER;

/**
 * A filter that logs the incoming HTTP requests and their processing time.
 */
@Singleton
public class RequestLoggingFilter extends Filter {

    @Inject
    public RequestLoggingFilter(Materializer mat) {
        super(mat);
    }

    @Override
    public CompletionStage<Result> apply(Function<Http.RequestHeader, CompletionStage<Result>> nextFilter,
                                         Http.RequestHeader requestHeader) {
        long startTime = System.currentTimeMillis();
        return nextFilter.apply(requestHeader)
                .thenApply(
                        result -> {
                            ALogger logger = getLogger(requestHeader);
                            long endTime = System.currentTimeMillis();
                            long requestTime = endTime - startTime;
                            Optional<User> signedinUser = Context.current().args().getOptional(SIGNEDIN_USER);

                            if (signedinUser.isPresent()) {
                                String username = signedinUser.map(User::getUsername).orElse("UNKNOWN");
                                String anonymizedUsername = StringUtils.anonymizeUsername(username);
                                logger.info("{} {} by {} took {}ms and returned {}",
                                        requestHeader.method(),
                                        requestHeader.uri(),
                                        anonymizedUsername,
                                        requestTime,
                                        result.status());
                            } else {
                                logger.info(
                                        "{} {} took {}ms and returned {}",
                                        requestHeader.method(),
                                        requestHeader.uri(),
                                        requestTime,
                                        result.status());
                            }

                            return result;
                        });
    }

    private ALogger getLogger(Http.RequestHeader requestHeader) {
        if (isApiRequest(requestHeader)) return Logger.of("api");
        if (isGuiRequest(requestHeader)) return Logger.of("gui");
        if (isPublixRequest(requestHeader)) return Logger.of("publix");
        return Logger.of("http");
    }

    private boolean isApiRequest(Http.RequestHeader requestHeader) {
        return requestHeader.path().contains("/jatos/api/");
    }

    private boolean isGuiRequest(Http.RequestHeader requestHeader) {
        return requestHeader.path().contains("/jatos/");
    }

    private boolean isPublixRequest(Http.RequestHeader requestHeader) {
        return requestHeader.path().contains("/publix/");
    }

}
