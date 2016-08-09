package services.publix.idcookie;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import services.publix.PublixErrorMessages;

/**
 * Wrapper around an ArrayList for IdCookies. Adds some useful methods. The
 * number of IdCookies are limited to to {@value #MAX_ID_COOKIES}.
 * 
 * @author Kristian Lange (2016)
 */
@SuppressWarnings("serial")
public class IdCookieContainer extends ArrayList<IdCookie> {

	/**
	 * Max number of ID cookies. If all cookies are used and a new study is
	 * started, the oldest ID cookie will be overwritten.
	 * 
	 * Important note: jatos.js supports only up to 10 cookies so far.
	 */
	private static final int MAX_ID_COOKIES = 10;

	public boolean isFull() {
		return this.size() >= MAX_ID_COOKIES;
	}

	/**
	 * Returns a number from 0 to {@value #MAX_ID_COOKIES}. It iterates through
	 * the IdCookies and returns the first index that isn't used. If this
	 * IdCookieContainer is full a IndexOutOfBoundsException will be thrown.
	 */
	public int getNextAvailableIdCookieIndex() {
		if (isFull()) {
			throw new IndexOutOfBoundsException(
					PublixErrorMessages.IDCOOKIE_CONTAINER_INDEX_OUT_OF_BOUND);
		}
		List<Integer> existingIndices = this.stream().map(c -> c.getIndex())
				.collect(Collectors.toList());
		for (int i = 0; i < MAX_ID_COOKIES; i++) {
			if (!existingIndices.contains(i)) {
				return i;
			}
		}
		throw new IndexOutOfBoundsException(
				PublixErrorMessages.IDCOOKIE_CONTAINER_INDEX_OUT_OF_BOUND);
	}

	public IdCookie findWithStudyResultId(long studyResultId) {
		for (IdCookie cookie : this) {
			Long cookieStudyResultId = cookie.getStudyResultId();
			if (cookieStudyResultId != null
					&& cookieStudyResultId.equals(studyResultId)) {
				return cookie;
			}
		}
		return null;
	}

}
