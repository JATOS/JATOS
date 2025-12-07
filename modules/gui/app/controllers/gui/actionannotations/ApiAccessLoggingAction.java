package controllers.gui.actionannotations;

import auth.gui.AuthService;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import play.Logger;
import play.Logger.ALogger;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.With;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.CompletionStage;

/**
 * Annotation definition for Play actions: logging of each action call
 *
 * @author Kristian
 */
public class ApiAccessLoggingAction extends Action<GuiAccessLogging> {

    @With(ApiAccessLoggingAction.class)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ApiAccessLogging {
    }

    private final ALogger apiLogger = Logger.of("api_access");

    public CompletionStage<Result> call(Http.Request request) {
        String username = "unknown";
        if (request.attrs().containsKey(AuthService.SIGNEDIN_USER)) {
            username = request.attrs().get(AuthService.SIGNEDIN_USER).getUsername();
        }
        apiLogger.info(request.method() + " " + request.uri() + " (" + username + ")");
        return delegate.call(request);
    }

}
