package services.publix.idcookie;

import controllers.publix.Publix;
import controllers.publix.workers.JatosPublix.JatosRun;
import daos.common.worker.WorkerType;
import general.common.Common;
import general.common.Http.Context;
import play.Logger;
import play.Logger.ALogger;
import play.libs.typedmap.TypedKey;
import play.mvc.Http;
import play.mvc.Http.Cookie;
import play.mvc.Http.Cookies;
import services.publix.PublixErrorMessages;
import services.publix.idcookie.exceptions.IdCookieCollectionFullException;
import services.publix.idcookie.exceptions.IdCookieMalformedException;
import utils.common.Helpers;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static play.mvc.Http.Cookie.builder;

/**
 * This class offers a simple interface to extract, log and discard JATOS ID cookies.
 *
 * Internally, this class accesses ID cookies in the HTTP Request or Response. It stores the extracted
 * {@link IdCookieModel} in a {@link IdCookieCollection} and then puts the collection in the request attr (request
 * scope).
 *
 * Each browser can run up to certain limit (defined in jatos.conf) studies at the same time. This means that there is
 * the same number of ID cookies stored in the browser as studies are currently running (although part of them might
 * be abandoned).
 *
 * @author Kristian Lange
 */
@Singleton
public class IdCookieAccessor {

    private static final ALogger LOGGER = Logger.of(IdCookieAccessor.class);

    private static final String COOKIE_EQUALS = "=";
    private static final String COOKIE_AND = "&";

    public static final TypedKey<IdCookieCollection> IDCOOKIE_TYPED_KEY = TypedKey.create(IdCookieCollection.class.getSimpleName());

    private final IdCookieSerialiser idCookieSerialiser;

    @Inject
    public IdCookieAccessor(IdCookieSerialiser idCookieSerialiser) {
        this.idCookieSerialiser = idCookieSerialiser;
    }

    /**
     * Returns the IdCookieCollection containing all ID cookies of this Request. Additionally, it stores this
     * IdCookieCollection in the RequestScope. All subsequent calls of this method will get the IdCookieCollection from
     * the RequestScope.
     */
    protected IdCookieCollection extract(Http.RequestHeader requestHeader) {
        if (Context.current().args().containsKey(IDCOOKIE_TYPED_KEY)) {
            return Context.current().args().get(IDCOOKIE_TYPED_KEY);
        } else {
            IdCookieCollection idCookieCollection = extractFromCookies(requestHeader.cookies());
            Context.current().args().put(IDCOOKIE_TYPED_KEY, idCookieCollection);
            return idCookieCollection;
        }
    }

    /**
     * Extracts all ID cookies from all the HTTP cookies and stores them into an {@link IdCookieCollection}. If a cookie
     * is malformed, it is discarded right away (removed from the Response).
     */
    public IdCookieCollection extractFromCookies(Cookies cookies) {
        IdCookieCollection idCookieCollection = new IdCookieCollection();
        for (Cookie cookie : cookies) {
            // Cookie names are case-insensitive
            if (cookie.name().toLowerCase().startsWith(IdCookieModel.ID_COOKIE_NAME.toLowerCase())) {
                try {
                    IdCookieModel idCookie = buildIdCookie(cookie);
                    idCookieCollection.add(idCookie);
                } catch (IdCookieMalformedException e) {
                    Context.current().response().discardCookie(cookie.name());
                    LOGGER.warn("Deleted malformed JATOS ID cookie: " + e.getMessage());
                }
            }
        }
        return idCookieCollection;
    }

    private IdCookieModel buildIdCookie(Cookie cookie) throws IdCookieMalformedException {
        IdCookieModel idCookie = new IdCookieModel();
        Map<String, String> cookieMap = getCookiesKeyValuePairs(cookie);
        idCookie.setName(cookie.name());
        idCookie.setIndex(getCookieIndex(cookie.name()));
        idCookie.setWorkerId(getValueAsLong(cookieMap, IdCookieModel.WORKER_ID, true, cookie.name()));
        idCookie.setWorkerType(getWorkerType(cookieMap, cookie.name()));
        idCookie.setBatchId(getValueAsLong(cookieMap, IdCookieModel.BATCH_ID, true, cookie.name()));
        idCookie.setStudyId(getValueAsLong(cookieMap, IdCookieModel.STUDY_ID, true, cookie.name()));
        idCookie.setStudyResultId(getValueAsLong(cookieMap, IdCookieModel.STUDY_RESULT_ID, true, cookie.name()));
        idCookie.setStudyResultUuid(getValueAsString(cookieMap, IdCookieModel.STUDY_RESULT_UUID, true, cookie.name()));
        idCookie.setComponentId(getValueAsLong(cookieMap, IdCookieModel.COMPONENT_ID, false, cookie.name()));
        idCookie.setComponentResultId(getValueAsLong(cookieMap, IdCookieModel.COMPONENT_RESULT_ID, false, cookie.name()));
        idCookie.setComponentPosition(getValueAsInt(cookieMap, IdCookieModel.COMPONENT_POSITION, false, cookie.name()));
        idCookie.setStudyAssets(getValueAsString(cookieMap, IdCookieModel.STUDY_ASSETS, true, cookie.name()));
        idCookie.setUrlBasePath(getValueAsString(cookieMap, IdCookieModel.URL_BASE_PATH, true, cookie.name()));
        idCookie.setJatosRun(valueOfJatosRun(cookieMap, cookie));
        idCookie.setCreationTime(getValueAsLong(cookieMap, IdCookieModel.CREATION_TIME, true, cookie.name()));
        return idCookie;
    }

    /**
     * Maps the ID cookie value for a JATOS run to the enum {@link JatosRun}. If the value can't be matched to an
     * instance of JatosRun, then null is returned.
     */
    private JatosRun valueOfJatosRun(Map<String, String> cookieMap, Cookie cookie) throws IdCookieMalformedException {
        try {
            return JatosRun.valueOf(getValueAsString(cookieMap, IdCookieModel.JATOS_RUN, false, cookie.name()));
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    /**
     * Returns the index of the ID cookie which is the suffix of its name. If the suffix is not a number, then
     * an IdCookieMalformedException is thrown.
     */
    private int getCookieIndex(String name) throws IdCookieMalformedException {
        String indexStr = name.replaceFirst("^" + IdCookieModel.ID_COOKIE_NAME + "_", "");
        try {
            return Integer.parseInt(indexStr);
        } catch (NumberFormatException e) {
            throw new IdCookieMalformedException(PublixErrorMessages.couldntExtractIndexFromIdCookieName(name));

        }
    }

    /**
     * Extract and returns a Map with the given Cookie's key-value pairs.
     */
    private Map<String, String> getCookiesKeyValuePairs(Cookie cookie) throws IdCookieMalformedException {
        Map<String, String> cookieKeyValuePairs = new HashMap<>();
        for (String pairStr : cookie.value().split(COOKIE_AND)) {
            addKeyValuePair(cookieKeyValuePairs, pairStr);
        }
        return cookieKeyValuePairs;
    }

    private void addKeyValuePair(Map<String, String> cookieKeyValuePairs, String pairStr)
            throws IdCookieMalformedException {
        String[] pairArray = pairStr.split(COOKIE_EQUALS);
        String key;
        String value;
        // Every pair should have 2 elements (a key and a value)
        if (pairArray.length == 0) {
            throw new IdCookieMalformedException("Couldn't extract key from ID cookie.");
        } else if (pairArray.length == 1) {
            key = pairArray[0];
            value = "";
        } else if (pairArray.length == 2) {
            key = pairArray[0];
            value = pairArray[1];
        } else {
            throw new IdCookieMalformedException("Wrong number of '&' in ID cookie.");
        }
        cookieKeyValuePairs.put(key, value);
    }

    /**
     * Searches the given map for the given key and returns the corresponding value as String. Throws a
     * MalformedIdCookieException if the key doesn't exist. If strict is true and the value is, "" it throws a
     * MalformedIdCookieException.
     */
    private String getValueAsString(Map<String, String> cookieMap, String key, boolean strict, String cookieName)
            throws IdCookieMalformedException {
        String valueStr = cookieMap.get(key);
        if (valueStr == null || valueStr.equals("null")) {
            if (strict) {
                throw new IdCookieMalformedException(PublixErrorMessages.couldntExtractFromIdCookie(cookieName, key));
            } else {
                return null;
            }
        }
        try {
            valueStr = Helpers.urlDecode(valueStr);
        } catch (Exception e) {
            throw new IdCookieMalformedException(PublixErrorMessages.couldntExtractFromIdCookie(cookieName, key));
        }
        if (strict && valueStr.trim().isEmpty()) {
            throw new IdCookieMalformedException(PublixErrorMessages.couldntExtractFromIdCookie(cookieName, key));
        }
        return valueStr;
    }

    /**
     * Searches the given map for the given key and returns the corresponding value as Long. Does some simple validation.
     *
     * @param cookieMap  Map with cookie's key-value pairs
     * @param key        Key to extract the value from
     * @param strict     If true, it doesn't accept null values and throws a MalformedIdCookieException. If false, it
     *                   just returns null.
     * @param cookieName Name of the cookie
     * @return Value that maps the key
     * @throws IdCookieMalformedException Throws a MalformedIdCookieException if a cookie value is malformed.
     */
    private Long getValueAsLong(Map<String, String> cookieMap, String key, boolean strict, String cookieName)
            throws IdCookieMalformedException {
        String valueStr = cookieMap.get(key);
        if ((valueStr == null || valueStr.equals("null")) && !strict) {
            return null;
        }
        try {
            assert valueStr != null;
            return Long.valueOf(valueStr);
        } catch (Exception e) {
            throw new IdCookieMalformedException(PublixErrorMessages.couldntExtractFromIdCookie(cookieName, key));
        }
    }

    /**
     * Searches the given map the given key and returns the corresponding value as Integer. Does some simple validation.
     *
     * @param cookieMap  Map with cookie's key-value pairs
     * @param key        Key to extract the value from
     * @param strict     If true, it doesn't accept null values and throws a MalformedIdCookieException. If false, it
     *                   just returns null.
     * @param cookieName Name of the cookie
     * @return Value that maps the key
     * @throws IdCookieMalformedException Throws a MalformedIdCookieException if a cookie value is malformed.
     */
    @SuppressWarnings("SameParameterValue")
    private Integer getValueAsInt(Map<String, String> cookieMap, String key, boolean strict, String cookieName)
            throws IdCookieMalformedException {
        String valueStr = cookieMap.get(key);
        if ((valueStr == null || valueStr.equals("null")) && !strict) {
            return null;
        }
        try {
            assert valueStr != null;
            return Integer.valueOf(valueStr);
        } catch (Exception e) {
            throw new IdCookieMalformedException(PublixErrorMessages.couldntExtractFromIdCookie(cookieName, key));
        }
    }

    private WorkerType getWorkerType(Map<String, String> cookieMap, String cookieName) throws IdCookieMalformedException {
        try {
            String valueStr = cookieMap.get(IdCookieModel.WORKER_TYPE);
            return WorkerType.fromWireValue(valueStr);
        } catch (Exception e) {
            throw new IdCookieMalformedException(PublixErrorMessages.couldntExtractFromIdCookie(cookieName, IdCookieModel.WORKER_TYPE));
        }
    }

    /**
     * Discards the ID cookie that corresponds to the given study result ID. If there is no such ID cookie, it does
     * nothing.
     */
    protected void discard(Http.RequestHeader requestHeader, long studyResultId) {
        IdCookieCollection idCookieCollection = extract(requestHeader);
        IdCookieModel idCookie = idCookieCollection.findWithStudyResultId(studyResultId);
        if (idCookie != null) {
            idCookieCollection.remove(idCookie);
            Context.current().args().put(IDCOOKIE_TYPED_KEY, idCookieCollection);
            Context.current().response().discardCookie(idCookie.getName(), Common.getJatosUrlBasePath());
        }
    }

    /**
     * Puts the given IdCookieModel in the Response. Additionally, it stores the ID cookie in the RequestScope. It uses
     * a large number as Max-Age for the cookie, so it is unlikely to ever expire.
     */
    void write(Http.RequestHeader requestHeader, IdCookieModel newIdCookie) throws IdCookieCollectionFullException {
        IdCookieCollection idCookieCollection = extract(requestHeader);
        idCookieCollection.put(newIdCookie);

        // Put changed idCookieCollection into Context
        Context.current().args().put(IDCOOKIE_TYPED_KEY, idCookieCollection);
    }

    /**
     * Generates a Play cookie from the given IdCookieModel.
     */
    public Cookie generatePlayCookie(IdCookieModel idCookie) {
        String cookieValue = idCookieSerialiser.asCookieValueString(idCookie);
        Http.CookieBuilder cookieBuilder = builder(idCookie.getName(), cookieValue)
                .withMaxAge(Duration.of(10000, ChronoUnit.DAYS))
                .withHttpOnly(false)
                .withPath(Common.getJatosUrlBasePath())
                .withSecure(Common.isIdCookiesSecure());
        // https://github.com/JATOS/JATOS/issues/208
        // https://github.com/JATOS/JATOS/issues/231
        if (Common.getIdCookiesSameSite() != null) cookieBuilder.withSameSite(Common.getIdCookiesSameSite());
        return cookieBuilder.build();
    }

}
