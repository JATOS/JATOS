package common;

import play.Application;
import play.GlobalSettings;
import play.Logger;
import play.libs.F.Promise;
import play.mvc.Http.RequestHeader;
import play.mvc.Results;
import play.mvc.SimpleResult;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

/**
 * Play's Global class. We use Guice for dependency injection.
 * 
 * @author Kristian Lange
 */
public class Global extends GlobalSettings {

	private static final String CLASS_NAME = Global.class.getSimpleName();
	public static final Injector INJECTOR = createInjector();

	@Override
	public <A> A getControllerInstance(Class<A> controllerClass)
			throws Exception {
		return INJECTOR.getInstance(controllerClass);
	}

	private static Injector createInjector() {
		return Guice.createInjector(new AbstractModule() {

			@Override
			protected void configure() {
			}
		});
	}

	@Override
	public void onStart(Application app) {
		Logger.info(CLASS_NAME + ".onStart: Application has started");
		// Do some JATOS specific initialisation
		INJECTOR.getInstance(Initializer.class).initialize();
	}

	@Override
	public Promise<SimpleResult> onError(RequestHeader request, Throwable t) {
		// Make sure no internal error msg is ever shown
		return Promise.<SimpleResult> pure(Results
				.internalServerError(views.html.error
						.render("Internal JATOS error")));
	}

	@Override
	public Promise<SimpleResult> onHandlerNotFound(RequestHeader request) {
		return Promise.<SimpleResult> pure(Results.notFound(views.html.error
				.render("Requested page \"" + request.uri()
						+ "\" doesn't exist.")));
	}

	@Override
	public Promise<SimpleResult> onBadRequest(RequestHeader request,
			String error) {
		return Promise.<SimpleResult> pure(Results.badRequest(views.html.error
				.render("bad request")));
	}

}
