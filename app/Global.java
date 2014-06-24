import play.*;
import play.mvc.*;
import play.mvc.Http.*;
import play.libs.F.*;

import static play.mvc.Results.*;

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
