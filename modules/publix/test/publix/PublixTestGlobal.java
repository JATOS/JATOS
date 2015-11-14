package publix;


import com.google.inject.Guice;
import com.google.inject.Injector;

import play.Application;
import play.GlobalSettings;
import play.Logger;
import play.libs.F.Promise;
import play.mvc.Http.RequestHeader;
import play.mvc.Result;
import play.mvc.Results;

/**
 * Play's Global class. We use Guice for dependency injection.
 * 
 * @author Kristian Lange
 */
public class PublixTestGlobal extends GlobalSettings {

	private static final String CLASS_NAME = PublixTestGlobal.class.getSimpleName();

	public static final Injector INJECTOR = createInjector();

	private static Injector createInjector() {
		return Guice.createInjector();
	}

	@Override
	public <A> A getControllerInstance(Class<A> controllerClass)
			throws Exception {
		return INJECTOR.getInstance(controllerClass);
	}

	@Override
	public void onStart(Application app) {
		Logger.info(CLASS_NAME + ".onStart: JATOS has started");
	}

	@Override
	public void onStop(Application app) {
		Logger.info(CLASS_NAME + ".onStop: JATOS shutdown");
	}

	@Override
	public Promise<Result> onError(RequestHeader request, Throwable t) {
		Logger.info(CLASS_NAME + ".onError: Internal JATOS error", t);
		return Promise.<Result> pure(Results
				.internalServerError());
	}

	@Override
	public Promise<Result> onHandlerNotFound(RequestHeader request) {
		Logger.info(CLASS_NAME + ".onHandlerNotFound: Requested page \""
				+ request.uri() + "\" doesn't exist.");
		return Promise.<Result> pure(Results.notFound());
	}

	@Override
	public Promise<Result> onBadRequest(RequestHeader request, String error) {
		Logger.info(CLASS_NAME + ".onBadRequest: " + error);
		return Promise.<Result> pure(Results.badRequest());
	}

}
