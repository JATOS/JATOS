package controllers.gui.actionannotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import controllers.gui.Users;
import controllers.gui.actionannotations.GuiLoggingAction.GuiLogging;
import play.Logger;
import play.Logger.ALogger;
import play.libs.F;
import play.mvc.Action;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Http.Request;
import play.mvc.Result;
import play.mvc.With;

/**
 * Annotation definition for Play actions: logging of each action call, e.g.
 * 'gui_access - GET /jatos/19/run (admin)'
 * 
 * @author Kristian Lange (2016)
 */
public class GuiLoggingAction extends Action<GuiLogging> {

	@With(GuiLoggingAction.class)
	@Target({ ElementType.TYPE, ElementType.METHOD })
	@Retention(RetentionPolicy.RUNTIME)
	public @interface GuiLogging {
	}

	private ALogger guiLogger = Logger.of("gui_access");

	public F.Promise<Result> call(Http.Context ctx) throws Throwable {
		final Request request = ctx.request();
		guiLogger.info(request.method() + " " + request.uri() + " ("
				+ Controller.session(Users.SESSION_EMAIL) + ")");
		return delegate.call(ctx);
	}

}
