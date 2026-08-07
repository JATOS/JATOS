package filters;

import general.common.Common;
import org.apache.pekko.stream.Materializer;
import http.common.Http.Context;
import models.common.User;
import play.Logger;
import play.Logger.ALogger;
import play.mvc.Filter;
import play.mvc.Http;
import play.mvc.Http.RequestHeader;
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
    public CompletionStage<Result> apply(Function<RequestHeader, CompletionStage<Result>> nextFilter,
                                         RequestHeader requestHeader) {
        long startTime = System.currentTimeMillis();
        Context context = requestHeader.attrs().get(Context.CONTEXT_TYPED_KEY);

        return nextFilter.apply(requestHeader)
                .thenApply(result -> Context.withContext(context, () -> {
                    Optional<ALogger> loggerOptional = getLogger(requestHeader);
                    if (loggerOptional.isEmpty()) {
                        return result;
                    }

                    long endTime = System.currentTimeMillis();
                    long requestTime = endTime - startTime;
                    Optional<User> signedinUser = Context.current().args().getOptional(SIGNEDIN_USER);

                    if (signedinUser.isPresent()) {
                        String username = signedinUser.map(User::getUsername).orElse("UNKNOWN");
                        String anonymizedUsername = StringUtils.anonymizeUsername(username);
                        loggerOptional.get().info("{} {} by {} took {}ms and returned {}",
                                requestHeader.method(),
                                requestHeader.uri(),
                                anonymizedUsername,
                                requestTime,
                                result.status());
                    } else {
                        loggerOptional.get().info(
                                "{} {} took {}ms and returned {}",
                                requestHeader.method(),
                                requestHeader.uri(),
                                requestTime,
                                result.status());
                    }

                    return result;
                }));
    }

    private Optional<ALogger> getLogger(RequestHeader requestHeader) {
        if (isApiRequest(requestHeader) && shouldLog("api")) return Optional.of(Logger.of("api"));
        if (isGuiRequest(requestHeader) && shouldLog("gui")) return Optional.of(Logger.of("gui"));
        if (isPublixRequest(requestHeader) && shouldLog("publix")) return Optional.of(Logger.of("publix"));
        if (isAssetsRequest(requestHeader) && shouldLog("assets")) return Optional.of(Logger.of("assets"));
        if (shouldLog("all")) return Optional.of(Logger.of("http"));
        return Optional.empty();
    }

    private boolean isApiRequest(RequestHeader requestHeader) {
        return requestHeader.path().startsWith(Common.getJatosUrlBasePath() + "jatos/api/");
    }

    private boolean isGuiRequest(RequestHeader requestHeader) {
        return requestHeader.path().startsWith(Common.getJatosUrlBasePath() + "jatos/");
    }

    private boolean isPublixRequest(RequestHeader requestHeader) {
        return requestHeader.path().startsWith(Common.getJatosUrlBasePath() + "publix/");
    }

    private boolean isAssetsRequest(RequestHeader requestHeader) {
        return requestHeader.path().startsWith(Common.getJatosUrlBasePath() + "assets/")
                || requestHeader.path().startsWith(Common.getJatosUrlBasePath() + "assets-nv/");
    }

    private boolean shouldLog(String category) {
        return Common.getLogsRequestCategories().contains("all")
                || Common.getLogsRequestCategories().contains(category);
    }

}
