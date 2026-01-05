package controllers.gui.actionannotations;

import auth.gui.AuthService;
import general.common.Http.Context;
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

import static auth.gui.AuthAction.SIGNEDIN_USER;
import static controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;

/**
 * Annotation definition for Play actions: logging of each action call
 *
 * @author Kristian Lange
 */
public class GuiAccessLoggingAction extends Action<GuiAccessLogging> {

    @With(GuiAccessLoggingAction.class)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface GuiAccessLogging {
    }

    private final ALogger guiLogger = Logger.of("gui_access");

    public CompletionStage<Result> call(Http.Request request) {
        String username = "unknown";
        if (request.session().get(AuthService.SESSION_USERNAME).isPresent()) {
            username = request.session().get(AuthService.SESSION_USERNAME).get();
        } else if (Context.current().args().containsKey(SIGNEDIN_USER)) {
            username = Context.current().args().get(SIGNEDIN_USER).getUsername();
        }
        guiLogger.info(request.method() + " " + request.uri() + " (" + username + ")");
        return delegate.call(request);
    }

}
