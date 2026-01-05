package filters;

import akka.stream.Materializer;
import general.common.Http.Context;
import play.mvc.Filter;
import play.mvc.Http;
import play.mvc.Http.RequestHeader;
import play.mvc.Result;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

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
                .whenComplete((result, throwable) -> {
                    // Ensure Context is restored for any async callbacks in subsequent filters
                    Context.setCurrent(context);
                })
                .handle((result, throwable) -> {
                    try {
                        if (throwable != null) {
                            // If an exception reached this far, rethrow or handle
                            throw new RuntimeException(throwable);
                        }
                        return result;
                    } finally {
                        // Final cleanup to prevent memory leaks in the thread pool
                        Context.clear();
                    }
                });
    }

}
