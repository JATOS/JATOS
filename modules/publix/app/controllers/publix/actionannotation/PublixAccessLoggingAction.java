package controllers.publix.actionannotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.CompletionStage;

import controllers.publix.actionannotation.PublixAccessLoggingAction.PublixAccessLogging;
import play.Logger;
import play.Logger.ALogger;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Http.Request;
import play.mvc.Result;
import play.mvc.With;

/**
 * Annotation definition for Play actions: logging of each action call, e.g.
 * 'publix_access - GET /publix/19/64/start'
 * 
 * @author Kristian Lange (2016)
 */
public class PublixAccessLoggingAction extends Action<PublixAccessLogging> {

	@With(PublixAccessLoggingAction.class)
	@Target({ ElementType.TYPE, ElementType.METHOD })
	@Retention(RetentionPolicy.RUNTIME)
	public @interface PublixAccessLogging {
	}

	private final ALogger logger = Logger.of("publix_access");

	public CompletionStage<Result> call(Http.Context ctx) {
		final Request request = ctx.request();
		logger.info(request.method() + " " + request.uri());
		return delegate.call(ctx);
	}

}
