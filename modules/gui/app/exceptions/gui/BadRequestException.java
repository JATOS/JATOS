package exceptions.gui;

import general.common.ApiEnvelope.ErrorCode;

import static play.mvc.Http.Status.BAD_REQUEST;

public class BadRequestException extends HttpException {

	public BadRequestException(String message) {
		super(BAD_REQUEST, message);
	}

	public BadRequestException(String message, ErrorCode errorCode) {
		super(BAD_REQUEST, message, errorCode);
	}

}
