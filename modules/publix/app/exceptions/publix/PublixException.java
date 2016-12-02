package exceptions.publix;

import org.apache.http.HttpStatus;

import play.mvc.Result;
import play.mvc.Results;
import utils.common.HttpUtils;

import com.google.common.net.MediaType;

/**
 * Causes a request to return with a HTTP return type that is defined in the
 * child classes of this abstract class.
 * 
 * @author Kristian Lange
 */
@SuppressWarnings("serial")
public abstract class PublixException extends Exception {

	protected MediaType mediaType = MediaType.HTML_UTF_8;
	protected int httpStatus = HttpStatus.SC_BAD_REQUEST;

	public PublixException(String message) {
		super(message);
	}

	public PublixException(String message, int httpStatus) {
		super(message);
		this.httpStatus = httpStatus;
	}

	public Result getSimpleResult() {
		if (!HttpUtils.isAjax()) {
			return Results.status(httpStatus,
					views.html.publix.error.render(getMessage()));
		} else {
			return Results.status(httpStatus, getMessage());
		}
	}

}
