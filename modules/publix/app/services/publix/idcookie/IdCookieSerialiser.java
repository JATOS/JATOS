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
    public String asCookieValueString(IdCookieModel idCookie) {
        StringBuilder sb = new StringBuilder();
        appendCookieEntry(sb, IdCookieModel.BATCH_ID, idCookie.getBatchId(), true);
        appendCookieEntry(sb, IdCookieModel.COMPONENT_ID, idCookie.getComponentId(), true);
        appendCookieEntry(sb, IdCookieModel.COMPONENT_POSITION, idCookie.getComponentPosition(), true);
        appendCookieEntry(sb, IdCookieModel.COMPONENT_RESULT_ID, idCookie.getComponentResultId(), true);
        appendCookieEntry(sb, IdCookieModel.CREATION_TIME, idCookie.getCreationTime(), true);
        appendCookieEntry(sb, IdCookieModel.STUDY_ASSETS, idCookie.getStudyAssets(), true);
        appendCookieEntry(sb, IdCookieModel.URL_BASE_PATH, idCookie.getUrlBasePath(), true);
        appendCookieEntry(sb, IdCookieModel.JATOS_RUN, idCookie.getJatosRun(), true);
        appendCookieEntry(sb, IdCookieModel.GROUP_RESULT_ID, idCookie.getGroupResultId(), true);
        appendCookieEntry(sb, IdCookieModel.STUDY_ID, idCookie.getStudyId(), true);
        appendCookieEntry(sb, IdCookieModel.STUDY_RESULT_ID, idCookie.getStudyResultId(), true);
        appendCookieEntry(sb, IdCookieModel.STUDY_RESULT_UUID, idCookie.getStudyResultUuid(), true);
        appendCookieEntry(sb, IdCookieModel.WORKER_ID, idCookie.getWorkerId(), true);
        appendCookieEntry(sb, IdCookieModel.WORKER_TYPE, idCookie.getWorkerType(), false);
        return sb.toString();
    }

    private void appendCookieEntry(StringBuilder sb, String key,
            Object value, boolean cookieAnd) {
        sb.append(key);
        sb.append(COOKIE_EQUALS);
        sb.append(value);
        if (cookieAnd) {
            sb.append(COOKIE_AND);
        }
    }
}
