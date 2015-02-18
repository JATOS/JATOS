package services;

import models.Messages;
import play.mvc.Http;

/**
 * Provides something similar to a request scope in Spring or Guice. Objects are
 * stored within Play's Http.Context which is created anew for each request.
 * 
 * @author Kristian Lange
 */
public class RequestScope {

	private static final String MESSAGES = "messages";

	public static Object get(String key) {
		return Http.Context.current().args.get(key);
	}

	public static void put(String key, Object value) {
		Http.Context.current().args.put(key, value);
	}

	public static Messages getMessages() {
		Messages messages = ((Messages) get(MESSAGES));
		if (messages == null) {
			messages = new Messages();
			put(MESSAGES, messages);
		}
		return messages;
	}

}
