package controllers.gui.actionannotations;

import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import play.Logger;
import play.Logger.ALogger;
import play.mvc.*;
import play.mvc.Http.Request;
import services.gui.AuthenticationService;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.CompletionStage;

/**
 * Annotation definition for Play actions: logging of each action call, e.g.
 * 'gui_access - GET /jatos/19/run (admin)'
 * 
 * @author Kristian Lange (2016)
 */
public class GuiAccessLoggingAction extends Action<GuiAccessLogging> {

	@With(GuiAccessLoggingAction.class)
	@Target({ ElementType.TYPE, ElementType.METHOD })
	@Retention(RetentionPolicy.RUNTIME)
	public @interface GuiAccessLogging {
	}

	private final ALogger guiLogger = Logger.of("gui_access");

	public CompletionStage<Result> call(Http.Context ctx) {
		final Request request = ctx.request();
		guiLogger.info(request.method() + " " + request.uri() + " ("
				+ Controller.session(AuthenticationService.SESSION_USERNAME)
				+ ")");
		return delegate.call(ctx);
	}

}
