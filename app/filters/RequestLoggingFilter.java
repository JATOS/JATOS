package filters;

import akka.stream.Materializer;
import play.Logger;
import play.Logger.ALogger;
import play.mvc.Filter;
import play.mvc.Http;
import play.mvc.Result;

import javax.inject.Inject;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public class RequestLoggingFilter extends Filter {

    private final ALogger logger = Logger.of("HTTP");

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
                            long endTime = System.currentTimeMillis();
                            long requestTime = endTime - startTime;
                            logger.info(
                                    "{} {} took {}ms and returned {}",
                                    requestHeader.method(),
                                    requestHeader.uri(),
                                    requestTime,
                                    result.status());
                            return result;
                        });
    }

}
