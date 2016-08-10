package general.common;

import play.mvc.Http;

/**
 * Provides something similar to a request scope in Spring or Guice. Objects are
 * stored within Play's Http.Context which is created anew for each request.
 * 
 * @author Kristian Lange
 */
public class RequestScope {

	public static Object get(String key) {
		return Http.Context.current().args.get(key);
	}

	public static void put(String key, Object value) {
		Http.Context.current().args.put(key, value);
	}

}
