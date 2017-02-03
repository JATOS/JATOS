package general;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.inject.Inject;
import javax.inject.Provider;

import play.Configuration;
import play.Environment;
import play.Logger;
import play.Logger.ALogger;
import play.api.OptionalSourceMapper;
import play.api.routing.Router;
import play.http.DefaultHttpErrorHandler;
import play.mvc.Http;
import play.mvc.Http.RequestHeader;
import play.mvc.Result;
import play.mvc.Results;

public class ErrorHandler extends DefaultHttpErrorHandler {

	private static final ALogger LOGGER = Logger.of(ErrorHandler.class);

	@Inject
	public ErrorHandler(Configuration configuration, Environment environment,
			OptionalSourceMapper sourceMapper, Provider<Router> routes) {
		super(configuration, environment, sourceMapper, routes);
	}

	public CompletionStage<Result> onClientError(RequestHeader request,
			int statusCode, String message) {
		switch (statusCode) {
		case Http.Status.BAD_REQUEST:
			LOGGER.info(".onBadRequest: " + message);
			return CompletableFuture.completedFuture(
					Results.badRequest(views.html.error.render("Bad request")));
		case Http.Status.NOT_FOUND:
			LOGGER.info(".onNotFound: Requested page \"" + request.uri()
					+ "\" couldn't be found.");
			return CompletableFuture.completedFuture(
					Results.notFound(views.html.error.render("Requested page \""
							+ request.uri() + "\" couldn't be found.")));
		case Http.Status.FORBIDDEN:
			LOGGER.info(".onForbidden: " + message);
			return CompletableFuture.completedFuture(Results
					.forbidden("You're not allowed to access this resource."));
		case Http.Status.REQUEST_ENTITY_TOO_LARGE:
			LOGGER.info(".onRequestEntityTooLarge: HTTP status code "
					+ statusCode + ", " + message);
			return CompletableFuture.completedFuture(Results.status(statusCode,
					"Request entity too large: You probably tried to upload a file that is too large"));
		default:
			LOGGER.info(".onClientError: HTTP status code " + statusCode + ", "
					+ message);
			return CompletableFuture.completedFuture(Results.status(statusCode,
					views.html.error.render("Internal JATOS error")));
		}
	}

	@Override
	public CompletionStage<Result> onServerError(RequestHeader request,
			Throwable exception) {
		LOGGER.info(".onServerError: Internal JATOS error", exception);
		return CompletableFuture.completedFuture(Results.internalServerError(
				views.html.error.render("Internal JATOS error")));
	}

}
