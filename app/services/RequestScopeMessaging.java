package services;

import models.Messages;
import utils.JsonUtils;

import common.RequestScope;

/**
 * Passes on messages (info/warning/error/success) to the view. Uses
 * RequestScope. JATOS has two similar messaging services, this and
 * FlashScopeMessaging. Difference to FlashScopeMessaging: several messages of
 * each kind and more reliable.
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
