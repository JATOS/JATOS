package services.publix;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import exceptions.publix.MalformedIdCookieException;
import play.Logger;
import play.Logger.ALogger;

/**
 * 
 * @author Kristian Lange (2016)
 */
@SuppressWarnings("serial")
public class IdCookieContainer extends ArrayList<IdCookie2> {

	private static final ALogger LOGGER = Logger.of(IdCookieContainer.class);

	/**
	 * Max number of ID cookies. If all cookies are used and a new study is
	 * started, the oldest ID cookie will be overwritten.
	 * 
	 * Important: jatos.js supports only up to 10 cookies so far.
	 */
	private static final int MAX_ID_COOKIES = 10;

	public boolean isFull() {
		return this.size() >= MAX_ID_COOKIES;
	}

	public int getNextCookieIndex() {
		if (isFull()) {
			throw new IndexOutOfBoundsException();
		}
		List<Integer> exisitingIndices = this.stream().map(c -> getCookieIndex(c))
				.collect(Collectors.toList());
		for (int i = 0; i < MAX_ID_COOKIES; i++) {
			if (!exisitingIndices.contains(i)) {
				return i;
			}
		}
		throw new IndexOutOfBoundsException();
	}

	/**
	 * Returns the index of the ID cookie which is in the last char of it's
	 * name. If the last char is not a number than -1 is returned.
	 */
	private int getCookieIndex(IdCookie2 cookie) {
		String name = cookie.getName();
		char lastChar = name.charAt(name.length() - 1);
		return Character.getNumericValue(lastChar);
	}

	public IdCookie2 findWithStudyResultId(long studyResultId) {
		for (IdCookie2 cookie : this) {
			try {
				if (cookie.getStudyResultId() == studyResultId) {
					return cookie;
				}
			} catch (MalformedIdCookieException e) {
				// Log and ignore
				LOGGER.warn(e.getMessage());
			}
		}
		return null;
	}

}
