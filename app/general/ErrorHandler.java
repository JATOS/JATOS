package general;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import exceptions.gui.JatosGuiException;
import exceptions.publix.InternalServerErrorPublixException;
import exceptions.publix.PublixException;
import play.Configuration;
import play.Environment;
import play.Logger;
import play.Logger.ALogger;
import play.api.OptionalSourceMapper;
import play.api.routing.Router;
import play.http.DefaultHttpErrorHandler;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Http.RequestHeader;
import utils.common.HttpUtils;
import play.mvc.Result;
import play.mvc.Results;

@Singleton
public class ErrorHandler extends DefaultHttpErrorHandler {

	private static final ALogger LOGGER = Logger.of(ErrorHandler.class);

	@Inject
	public ErrorHandler(Configuration configuration, Environment environment,
			OptionalSourceMapper sourceMapper, Provider<Router> routes) {
		super(configuration, environment, sourceMapper, routes);
	}

	@Override
	public CompletionStage<Result> onClientError(RequestHeader request,
			int statusCode, String message) {
		// Log error messages and show some message - but don't
		// show any longer message (e.g. with stack trace) to a worker
		Result result;
		switch (statusCode) {
		case Http.Status.BAD_REQUEST:
			LOGGER.info(".onClientError - bad request: " + message);
			result = Results.badRequest("Bad request");
			break;
		case Http.Status.NOT_FOUND:
			LOGGER.info(".onClientError - not found: Requested page \""
					+ request.uri() + "\" couldn't be found.");
			result = Results.notFound("Requested page \"" + request.uri()
					+ "\" couldn't be found.");
			break;
		case Http.Status.FORBIDDEN:
			LOGGER.info(".onClientError - forbidden: " + message);
			result = Results
					.forbidden("You're not allowed to access this resource.");
			break;
		case Http.Status.REQUEST_ENTITY_TOO_LARGE:
			LOGGER.info(
					".onClientError - request entity too large: " + message);
			result = Results.status(statusCode,
					"Request entity too large: You probably tried"
							+ " to upload a file that is too large");
			break;
		default:
			LOGGER.warn(".onClientError - HTTP status code " + statusCode + ": "
					+ message);
			result = Results.status(statusCode, "JATOS error: " + statusCode);
			break;
		}
		return CompletableFuture.completedFuture(result);
	}

	@Override
	public CompletionStage<Result> onServerError(RequestHeader request,
			Throwable t) {
		// We use Play's onServerError() to catch JATOS' JatosGuiExceptions and
		// PublixException. Those exceptions come with a their own result. We
		// log the exception and show this result.
		Result result;

		if (t instanceof JatosGuiException) {
			JatosGuiException e = (JatosGuiException) t;
			LOGGER.info(".onServerError - JatosGuiException during call "
					+ Controller.request().uri() + ": " + e.getMessage());
			result = e.getSimpleResult();

		} else if (t instanceof InternalServerErrorPublixException) {
			InternalServerErrorPublixException e = (InternalServerErrorPublixException) t;
			LOGGER.error(
					".onServerError - InternalServerErrorPublixException during call "
							+ Controller.request().uri() + ": "
							+ e.getMessage(),
					e);
			result = e.getSimpleResult();

		} else if (t instanceof PublixException) {
			PublixException e = (PublixException) t;
			LOGGER.info(".onServerError - PublixException during call "
					+ Controller.request().uri() + ": " + e.getMessage());
			result = e.getSimpleResult();

		} else {
			LOGGER.error(".onServerError - Internal JATOS error", t);
			String msg = "Internal JATOS error during "
					+ Controller.request().uri()
					+ ". Check logs to get more information.";
			if (HttpUtils.isAjax()) {
				result = Results.internalServerError(msg);
			} else {
				result = Results
						.internalServerError(views.html.error.render(msg));
			}
		}

		return CompletableFuture.completedFuture(result);
	}

}
