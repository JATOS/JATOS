package services.gui;

import java.io.IOException;

import play.api.mvc.Call;
import play.mvc.Http;
import play.mvc.Results;
import play.mvc.SimpleResult;
import services.FlashScopeMessaging;
import services.RequestScopeMessaging;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import controllers.gui.ControllerUtils;
import controllers.gui.Home;
import controllers.gui.Studies;
import exceptions.BadRequestException;
import exceptions.ForbiddenException;
import exceptions.NotFoundException;
import exceptions.gui.JatosGuiException;

/**
 * Class with convenience methods to throw a {@link JatosGuiException}
 * 
 * @author Kristian Lange
 */
@Singleton
public class JatosGuiExceptionThrower {

	private final Provider<Home> homeProvider;
	private final Provider<Studies> studiesProvider;

	@Inject
	JatosGuiExceptionThrower(Provider<Studies> studiesProvider,
			Provider<Home> homeProvider) {
		this.homeProvider = homeProvider;
		this.studiesProvider = studiesProvider;
	}

	/**
	 * Throws a JatosGuiException for an Ajax request (doesn't return a view)
	 * with the given error msg and HTTP status.
	 */
	public void throwAjax(String errorMsg, int httpStatus)
			throws JatosGuiException {
		SimpleResult result = Results.status(httpStatus, errorMsg);
		throw new JatosGuiException(result, errorMsg);
	}

	/**
	 * Throws a JatosGuiException for an Ajax request (doesn't return a view but
	 * a simple text) with the exception's message. The exception's type
	 * determines the response's HTTP status code.
	 */
	public void throwAjax(Exception e) throws JatosGuiException {
		int httpStatus = getHttpStatusFromException(e);
		SimpleResult result = Results.status(httpStatus, e.getMessage());
		throw new JatosGuiException(result, e.getMessage());
	}

	/**
	 * Throws a JatosGuiException that either redirects to the given call if
	 * it's a non-Ajax request - or returns the exception's message if it's a
	 * Ajax request. The exception's type determines the response's HTTP status
	 * code.
	 */
	public void throwRedirect(Exception e, Call call) throws JatosGuiException {
		SimpleResult result;
		if (ControllerUtils.isAjax()) {
			int statusCode = getHttpStatusFromException(e);
			result = Results.status(statusCode, e.getMessage());
		} else {
			FlashScopeMessaging.error(e.getMessage());
			result = Results.redirect(call);
		}
		throw new JatosGuiException(result, e.getMessage());
	}

	/**
	 * Throws a JatosGuiException with the given error msg and HTTP status. If
	 * non Ajax it shows home view. Distinguishes between normal and Ajax
	 * request.
	 */
	public void throwHome(String errorMsg, int httpStatus)
			throws JatosGuiException {
		SimpleResult result = null;
		if (ControllerUtils.isAjax()) {
			result = Results.status(httpStatus, errorMsg);
		} else {
			RequestScopeMessaging.error(errorMsg);
			result = (SimpleResult) homeProvider.get().home(httpStatus);
		}
		throw new JatosGuiException(result, errorMsg);
	}

	/**
	 * Throws a JatosGuiException. If it's a non-Ajax request, it puts the
	 * exception's message into the request scope and returns the home view. If
	 * it's a Ajax request, it just returns the exception's message. The HTTP
	 * status code is determined by the exception type.
	 */
	public void throwHome(Exception e) throws JatosGuiException {
		SimpleResult result = null;
		int httpStatus = getHttpStatusFromException(e);
		if (ControllerUtils.isAjax()) {
			result = Results.status(httpStatus, e.getMessage());
		} else {
			RequestScopeMessaging.error(e.getMessage());
			result = (SimpleResult) homeProvider.get().home(httpStatus);
		}
		throw new JatosGuiException(result, e.getMessage());
	}

	public void throwResult(Exception e, SimpleResult result)
			throws JatosGuiException {
		int httpStatus = getHttpStatusFromException(e);
		if (ControllerUtils.isAjax()) {
			result = Results.status(httpStatus, e.getMessage());
		} else {
			RequestScopeMessaging.error(e.getMessage());
		}
		throw new JatosGuiException(result, e.getMessage());
	}

	/**
	 * Throws a JatosGuiException with the given error msg and HTTP status. If
	 * non Ajax it shows study's index view. Distinguishes between normal and
	 * Ajax request.
	 */
	public void throwStudies(String errorMsg, int httpStatus, Long studyId)
			throws JatosGuiException {
		SimpleResult result = null;
		if (ControllerUtils.isAjax()) {
			result = Results.status(httpStatus, errorMsg);
		} else {
			RequestScopeMessaging.error(errorMsg);
			result = (SimpleResult) studiesProvider.get().index(studyId,
					httpStatus);
		}
		throw new JatosGuiException(result, errorMsg);
	}

	/**
	 * Throws a JatosGuiException. If it's a non-Ajax request, it puts the
	 * exception's message into the request scope and returns the study's index
	 * view. If it's a Ajax request, it just returns the exception's message.
	 * The HTTP status code is determined by the exception type.
	 */
	public void throwStudyIndex(Exception e, Long studyId)
			throws JatosGuiException {
		SimpleResult result = null;
		int httpStatus = getHttpStatusFromException(e);
		if (ControllerUtils.isAjax()) {
			result = Results.status(httpStatus, e.getMessage());
		} else {
			RequestScopeMessaging.error(e.getMessage());
			result = (SimpleResult) studiesProvider.get().index(studyId,
					httpStatus);
		}
		throw new JatosGuiException(result, e.getMessage());
	}

	private int getHttpStatusFromException(Exception e) {
		if (e instanceof ForbiddenException) {
			return Http.Status.FORBIDDEN;
		} else if (e instanceof BadRequestException) {
			return Http.Status.BAD_REQUEST;
		} else if (e instanceof IOException) {
			return Http.Status.BAD_REQUEST;
		} else if (e instanceof NotFoundException) {
			return Http.Status.NOT_FOUND;
		} else {
			return Http.Status.INTERNAL_SERVER_ERROR;
		}
	}

}
