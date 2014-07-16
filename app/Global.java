import static play.mvc.Results.badRequest;
import static play.mvc.Results.internalServerError;
import static play.mvc.Results.notFound;
import play.GlobalSettings;
import play.Logger;
import play.libs.F.Promise;
import play.mvc.Http.RequestHeader;
import play.mvc.SimpleResult;

public class Global extends GlobalSettings {

	@Override
	public Promise<SimpleResult> onError(RequestHeader request, Throwable t) {
		Logger.error(t.getMessage());
		return Promise
				.<SimpleResult> pure(internalServerError(views.html.publix.error
						.render("Internal server error")));
	}

	@Override
	public Promise<SimpleResult> onHandlerNotFound(RequestHeader request) {
		return Promise.<SimpleResult> pure(notFound(views.html.publix.error
				.render("Requested page \"" + request.uri()
						+ "\" doesn't exist.")));
	}

	@Override
	public Promise<SimpleResult> onBadRequest(RequestHeader request,
			String error) {
		return Promise.<SimpleResult> pure(badRequest("bad request"));
	}

}
