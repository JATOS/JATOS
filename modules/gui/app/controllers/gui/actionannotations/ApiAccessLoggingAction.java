package controllers.gui.actionannotations;

import auth.gui.AuthService;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import general.common.RequestScope;
import models.common.User;
import play.Logger;
import play.Logger.ALogger;
import play.mvc.*;
import play.mvc.Http.Request;

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
@SuppressWarnings("deprecation")
public class ApiAccessLoggingAction extends Action<GuiAccessLogging> {

	@With(ApiAccessLoggingAction.class)
	@Target({ ElementType.TYPE, ElementType.METHOD })
	@Retention(RetentionPolicy.RUNTIME)
	public @interface ApiAccessLogging {
	}

	private final ALogger apiLogger = Logger.of("api_access");

	public CompletionStage<Result> call(Http.Context ctx) {
		final Request request = ctx.request();
		String username = "unknown";
		if (RequestScope.get(AuthService.LOGGED_IN_USER) != null) {
			username = ((User) RequestScope.get(AuthService.LOGGED_IN_USER)).getUsername();
		}
		apiLogger.info(request.method() + " " + request.uri() + " (" + username	+ ")");
		return delegate.call(ctx);
	}

}
