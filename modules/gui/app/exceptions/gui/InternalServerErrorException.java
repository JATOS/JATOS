package exceptions.gui;

import general.common.ApiEnvelope.ErrorCode;

import static play.mvc.Http.Status.INTERNAL_SERVER_ERROR;

public class InternalServerErrorException extends HttpException {

	public InternalServerErrorException(String message) {
		super(INTERNAL_SERVER_ERROR, message);
	}

	public InternalServerErrorException(String message, ErrorCode errorCode) {
		super(INTERNAL_SERVER_ERROR, message, errorCode);
	}

}
