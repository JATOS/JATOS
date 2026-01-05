package actions.common;

import general.common.Http.Context;
import general.common.IOExecutor;
import general.common.StudyAssetsExecutor;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.With;

import javax.inject.Inject;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static java.util.concurrent.CompletableFuture.supplyAsync;

public class AsyncAction extends Action<AsyncAction.Async> {

    public enum Executor {
        IO, // For everything that reads/writes from/to the database or file system
        STUDY_ASSETS, // For everything that reads/writes from/to the study assets directory
        DEFAULT // For the standard Play dispatcher (should not be used for blocking operations)
    }

    @With(AsyncAction.class)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Async {
        Executor value() default Executor.DEFAULT;
    }

    private final Map<Executor, java.util.concurrent.Executor> executors;

    @Inject
    public AsyncAction(IOExecutor ioExecutor, StudyAssetsExecutor studyAssetsExecutor) {
        EnumMap<Executor, java.util.concurrent.Executor> map = new EnumMap<>(Executor.class);
        map.put(Executor.IO, ioExecutor);
        map.put(Executor.STUDY_ASSETS, studyAssetsExecutor);
        this.executors = map;
    }

    @Override
    public CompletionStage<Result> call(Http.Request req) {
        Executor executor = (configuration != null) ? configuration.value() : Executor.DEFAULT;

        if (executor == Executor.DEFAULT) {
            return delegate.call(req);
        } else {
            // Context is already set by ContextFilter
            Context context = Context.current();

            return supplyAsync(() -> {
                try {
                    // Propagate context to the IO/StudyAssets thread
                    Context.setCurrent(context);
                    return delegate.call(req);
                } finally {
                    // Always clear Context to prevent memory leaks
                    Context.clear();
                }
            }, executors.get(executor)).thenCompose(stage -> stage);
        }
    }
}
