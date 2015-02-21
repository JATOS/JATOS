package services;

import models.Messages;
import play.mvc.Controller;
import utils.JsonUtils;

/**
 * Passes on messages (info/warning/error/success) to the view. Uses Play's
 * flash scope. JATOS has two similar messaging services, this and one using
 * RequestScopeMessaging. Difference to RequestScopeMessaging: only one of each
 * kind and Play's flash scope isn't reliable.
 * 
 * @author Kristian Lange
 */
public class FlashScopeMessaging {
	
	public static final String INFO = "info";
	public static final String SUCCESS = "success";
	public static final String ERROR = "error";
	public static final String WARNING = "warning";

	public static String getAsJson() {
		Messages messages = null;
		String info = Controller.flash().get(INFO);
		String success = Controller.flash().get(SUCCESS);
		String error = Controller.flash().get(ERROR);
		String warning = Controller.flash().get(WARNING);
		if (info != null || success != null || error != null || warning != null) {
			messages = new Messages();
			messages.info(info);
			messages.success(success);
			messages.error(error);
			messages.warning(warning);
		}
		return JsonUtils.asJson(messages);
	}

	public static void info(String msg) {
		if (msg != null) {
			Controller.flash(INFO, msg);
		}
	}

	public static void success(String msg) {
		if (msg != null) {
			Controller.flash(SUCCESS);
		}
	}

	public static void error(String msg) {
		if (msg != null) {
			Controller.flash(ERROR);
		}
	}

	public static void warning(String msg) {
		if (msg != null) {
			Controller.flash(WARNING);
		}
	}

}
