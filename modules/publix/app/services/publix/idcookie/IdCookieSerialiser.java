package services.publix.idcookie;

import javax.inject.Singleton;

/**
 * This class offers methods to turn an IdCookie into a string that can be put
 * into an URL header.
 * 
 * @author Kristian Lange (2017)
 */
@Singleton
public class IdCookieSerialiser {

	protected static final String COOKIE_EQUALS = "=";
	protected static final String COOKIE_AND = "&";

	/**
	 * Takes an IdCookie and put its fields into a String for an cookie value:
	 * key=value&key=value&... (similar to a URL query).
	 */
	public String asCookieValueString(IdCookie idCookie) {
		StringBuilder sb = new StringBuilder();
		appendCookieEntry(sb, IdCookie.BATCH_ID, idCookie.getBatchId(), true);
		appendCookieEntry(sb, IdCookie.COMPONENT_ID, idCookie.getComponentId(),
				true);
		appendCookieEntry(sb, IdCookie.COMPONENT_POSITION,
				idCookie.getComponentPosition(), true);
		appendCookieEntry(sb, IdCookie.COMPONENT_RESULT_ID,
				idCookie.getComponentResultId(), true);
		appendCookieEntry(sb, IdCookie.CREATION_TIME,
				idCookie.getCreationTime(), true);
		appendCookieEntry(sb, IdCookie.STUDY_ASSETS, idCookie.getStudyAssets(),
				true);
		appendCookieEntry(sb, IdCookie.JATOS_RUN, idCookie.getJatosRun(), true);
		appendCookieEntry(sb, IdCookie.GROUP_RESULT_ID,
				idCookie.getGroupResultId(), true);
		appendCookieEntry(sb, IdCookie.STUDY_ID, idCookie.getStudyId(), true);
		appendCookieEntry(sb, IdCookie.STUDY_RESULT_ID,
				idCookie.getStudyResultId(), true);
		appendCookieEntry(sb, IdCookie.WORKER_ID, idCookie.getWorkerId(), true);
		appendCookieEntry(sb, IdCookie.WORKER_TYPE, idCookie.getWorkerType(),
				false);
		return sb.toString();
	}

	private StringBuilder appendCookieEntry(StringBuilder sb, String key,
			Object value, boolean cookieAnd) {
		sb.append(key);
		sb.append(COOKIE_EQUALS);
		sb.append(value);
		if (cookieAnd) {
			sb.append(COOKIE_AND);
		}
		return sb;
	}
}
