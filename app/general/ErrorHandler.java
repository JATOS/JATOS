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
		// Log error messages and show some message in the JATOS GUI - but don't
		// show any longer message (e.g. with stack trace) to a worker
		switch (statusCode) {
		case Http.Status.BAD_REQUEST:
			LOGGER.info(".onClientError - bad request: " + message);
			return CompletableFuture.completedFuture(
					Results.badRequest(views.html.error.render("Bad request")));
		case Http.Status.NOT_FOUND:
			LOGGER.info(".onClientError - not found: Requested page \""
					+ request.uri() + "\" couldn't be found.");
			return CompletableFuture.completedFuture(
					Results.notFound(views.html.error.render("Requested page \""
							+ request.uri() + "\" couldn't be found.")));
		case Http.Status.FORBIDDEN:
			LOGGER.info(".onClientError - forbidden: " + message);
			return CompletableFuture.completedFuture(Results
					.forbidden("You're not allowed to access this resource."));
		case Http.Status.REQUEST_ENTITY_TOO_LARGE:
			LOGGER.info(
					".onClientError - request entity too large: " + message);
			return CompletableFuture.completedFuture(Results.status(statusCode,
					"Request entity too large: You probably tried to upload a file that is too large"));
		default:
			LOGGER.warn(".onClientError - HTTP status code " + statusCode + ": "
					+ message);
			return CompletableFuture.completedFuture(Results.status(statusCode,
					views.html.error.render("JATOS error: " + statusCode)));
		}
	}

	@Override
	public CompletionStage<Result> onServerError(RequestHeader request,
			Throwable t) {
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
