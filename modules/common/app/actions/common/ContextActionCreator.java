package actions.common;

import http.common.Http.Context;
import play.http.ActionCreator;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Result;

import java.lang.reflect.Method;
import java.util.concurrent.CompletionStage;

/**
 * Play {@link ActionCreator} that binds the request's HTTP {@link Context} to the worker thread for the duration of
 * every Java controller action invocation.
 *
 * It retrieves the {@link Context} attached to the request attributes by ContextFilter (or creates a fallback context
 * if none is present) and wraps the action execution inside
 * {@link Context#withContext(Context, java.util.function.Supplier)}.
 */
public class ContextActionCreator implements ActionCreator {

    @Override
    @SuppressWarnings("rawtypes")
    public Action createAction(Http.Request request, Method actionMethod) {
        return new Action.Simple() {
            @Override
            public CompletionStage<Result> call(Http.Request req) {
                Context context = req.attrs().getOptional(Context.CONTEXT_TYPED_KEY)
                        .orElseGet(() -> new Context(req));
                return Context.withContext(context, () -> delegate.call(req));
            }
        };
    }
}
