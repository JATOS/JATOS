package actions.common;

import http.common.Http.Context;
import executor.common.IOExecutor;
import executor.common.StudyAssetsExecutor;
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
import java.util.function.Function;

import static java.util.concurrent.CompletableFuture.supplyAsync;

/**
 * An asynchronous action that routes work to a specific executor. This action allows
 * delegating blocking operations (e.g., I/O or file system
 * operations) to custom execution contexts in order to avoid blocking Play's default
 * dispatcher.
 *
 * This class facilitates the use of the {@link AsyncAction.Async} annotation, which specifies
 * the executor context to be used during the execution of the associated route or controller
 * method. The supported executors are defined in the {@link AsyncAction.Executor} enum.
 *
 * To switch execution contexts for a specific route or controller method, use the
 * {@link AsyncAction.Async} annotation and specify the desired {@link AsyncAction.Executor}.
 * If no executor is specified, the {@link AsyncAction.Executor#DEFAULT} executor is used
 * as the fallback.
 *
 * Internally, this class ensures proper management of the request context when switching
 * execution contexts. The context is set before executing the asynchronous work in the
 * specified executor and cleared after execution to prevent memory leaks.
 */
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

            return supplyAsync(() ->
                    Context.withContext(context, () -> delegate.call(req)), executors.get(executor))
                    .thenCompose(Function.identity());
        }
    }
}
