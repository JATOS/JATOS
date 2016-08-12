package general.common;

import play.mvc.Http;

/**
 * Provides something similar to a request scope in Spring or Guice. Objects are
 * stored within Play's Http.Context which is created anew for each request.
 * It's a very useful storage that lasts just for the current request. You can
 * e.g. store a StudyRequest that you retrieved from the database and reuse it
 * later on without having to retrieve it from the database again. This saves
 * resources and brings performance.
 * 
 * @author Kristian Lange
 */
public class RequestScope {

	public static Object get(String key) {
		return Http.Context.current().args.get(key);
	}

	public static boolean has(String key) {
		return (get(key) != null);
	}

	public static void put(String key, Object value) {
		Http.Context.current().args.put(key, value);
	}

}
