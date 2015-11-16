package general;

import javax.inject.Inject;
import javax.inject.Provider;

import play.Configuration;
import play.Environment;
import play.Logger;
import play.api.OptionalSourceMapper;
import play.api.routing.Router;
import play.http.DefaultHttpErrorHandler;
import play.libs.F.Promise;
import play.mvc.Http.RequestHeader;
import play.mvc.Result;
import play.mvc.Results;

public class ErrorHandler extends DefaultHttpErrorHandler {

	private static final String CLASS_NAME = ErrorHandler.class.getSimpleName();

	@Inject
	public ErrorHandler(Configuration configuration, Environment environment,
			OptionalSourceMapper sourceMapper, Provider<Router> routes) {
		super(configuration, environment, sourceMapper, routes);
	}

	@Override
	protected Promise<Result> onForbidden(RequestHeader request, String message) {
		Logger.info(CLASS_NAME + ".onForbidden: " + message);
		return Promise.<Result> pure(Results
				.forbidden("You're not allowed to access this resource."));
	}

	@Override
	public Promise<Result> onNotFound(RequestHeader request, String message) {
		Logger.info(CLASS_NAME + ".onNotFound: Requested page \""
				+ request.uri() + "\" couldn't be found.");
		return Promise.<Result> pure(Results.notFound(views.html.error
				.render("Requested page \"" + request.uri()
						+ "\" couldn't be found.")));
	}

	@Override
	public Promise<Result> onBadRequest(RequestHeader request, String error) {
		Logger.info(CLASS_NAME + ".onBadRequest: " + error);
		return Promise.<Result> pure(Results.badRequest(views.html.error
				.render("Bad request")));
	}

	@Override
	public Promise<Result> onServerError(RequestHeader request,
			Throwable exception) {
		Logger.info(CLASS_NAME + ".onServerError: Internal JATOS error",
				exception);
		return Promise.<Result> pure(Results
				.internalServerError(views.html.error
						.render("Internal JATOS error")));
	}

}
