package general.gui;

import models.gui.Messages;
import utils.common.JsonUtils;

/**
 * Passes on messages (info/warning/error/success) to the view. Uses
 * RequestScope. JATOS has two similar messaging services, this and
 * FlashScopeMessaging. Difference to RequestScopeMessaging: only one of each
 * kind, but it survives a redirect (according to Play's documentation, flash
 * scope isn't reliable).
 * 
 * @author Kristian Lange
 */
public class RequestScopeMessaging {

	public static final String MESSAGES = "messages";

	public static String getAsJson() {
		Messages messages = ((Messages) RequestScope.get(MESSAGES));
		return JsonUtils.asJson(messages);
	}
	
	private static Messages getOrCreate() {
		Messages messages = ((Messages) RequestScope.get(MESSAGES));
		if (messages == null) {
			messages = new Messages();
			RequestScope.put(MESSAGES, messages);
		}
		return messages;
	}

	public static void error(String msg) {
		if (msg != null) {
			getOrCreate().error(msg);
		}
	}

	public static void info(String msg) {
		if (msg != null) {
			getOrCreate().info(msg);
		}
	}

	public static void warning(String msg) {
		if (msg != null) {
			getOrCreate().warning(msg);
		}
	}

	public static void success(String msg) {
		if (msg != null) {
			getOrCreate().success(msg);
		}
	}

}
