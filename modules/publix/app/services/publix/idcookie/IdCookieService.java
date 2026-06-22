package services.publix.idcookie;

import controllers.publix.workers.JatosPublix.JatosRun;
import daos.common.worker.WorkerType;
import general.common.Common;
import http.common.Http;
import http.common.HttpUtils;
import models.common.ComponentResult;
import models.common.StudyResult;
import play.Logger;
import play.libs.typedmap.TypedKey;
import play.mvc.Http.Cookie;
import play.mvc.Http.Cookies;
import services.publix.PublixErrorMessages;
import services.publix.idcookie.exceptions.IdCookieMalformedException;
import services.publix.idcookie.exceptions.IdCookieNotFoundException;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static play.mvc.Http.Cookie.builder;

/**
 * Service class for JATOS ID cookie handling.
 *
 * The request-scoped {@link IdCookieCollection} stored in {@link Http.Context#args()} is the source of truth for ID
 * cookies during request processing. This service initializes that collection from incoming browser cookies, lets the
 * application code query and mutate it, and finally synchronizes the collection back into response cookies.
 */
@Singleton
public class IdCookieService {

    private static final Logger.ALogger LOGGER = Logger.of(IdCookieService.class);

    private static final String COOKIE_EQUALS = "=";
    private static final String COOKIE_AND = "&";

    public static final TypedKey<Set<String>> INCOMING_IDCOOKIE_NAMES_TYPED_KEY = TypedKey.create("incomingIdCookieNames");
    public static final TypedKey<IdCookieCollection> IDCOOKIES_TYPED_KEY = TypedKey.create("idCookies");

    private final IdCookieSerialiser idCookieSerialiser;

    @Inject
    public IdCookieService(IdCookieSerialiser idCookieSerialiser) {
        this.idCookieSerialiser = idCookieSerialiser;
    }

    /**
     * Initializes the {@link Http.Context#args()} with ID cookies
     */
    public void initFromRequestCookies(Cookies cookies) {
        Http.Context.current().args().put(INCOMING_IDCOOKIE_NAMES_TYPED_KEY, extractIdCookieNames(cookies));
        Http.Context.current().args().put(IDCOOKIES_TYPED_KEY, extractFromCookies(cookies));
    }

    /**
     * Synchronizes the ID cookies from {@link Http.Context#args()} with ID cookies in the response. ID cookies that
     * were removed from {@link Http.Context#args()} during request handling are added as discard cookies.
     */
    public void syncIdCookiesToResponse() {
        Set<String> incomingCookieNames = Http.Context.current().args().get(INCOMING_IDCOOKIE_NAMES_TYPED_KEY);
        Set<String> finalCookieNames = generatePlayCookieNames();

        Set<String> removedCookieNames = new HashSet<>(incomingCookieNames);
        removedCookieNames.removeAll(finalCookieNames);

        Http.Context.current().response().setCookies(generatePlayCookies());
        Http.Context.current().response().setCookies(generateDiscardCookies(removedCookieNames));
    }

    public IdCookieCollection idCookies() {
        return Http.Context.current().args().get(IDCOOKIES_TYPED_KEY);
    }

    /**
     * Returns true if the ID cookie for the given study result ID exists in {@link Http.Context#args()}
     */
    public boolean hasIdCookie(Long studyResultId) {
        return idCookies().findWithStudyResultId(studyResultId) != null;
    }

    /**
     * Returns the IdCookieModel that corresponds to the given study result ID. If the cookie doesn't exist, it throws a
     * BadRequestException.
     */
    public IdCookieModel getIdCookie(Long studyResultId) {
        IdCookieModel idCookie = idCookies().findWithStudyResultId(studyResultId);
        if (idCookie == null) {
            throw new IdCookieNotFoundException(PublixErrorMessages.idCookieForThisStudyResultNotExists(studyResultId));
        }
        return idCookie;
    }

    /**
     * Returns true if the study assets of at least one ID cookie are equal to the given study assets. Otherwise,
     * returns false.
     */
    public boolean oneIdCookieHasThisStudyAssets(String studyAssets) {
        for (IdCookieModel idCookie : idCookies().getAll()) {
            if (idCookie.getStudyAssets().equals(studyAssets)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Generates an ID cookie from the given parameters and sets it in the response object.
     */
    public void writeIdCookie(StudyResult studyResult) {
        writeIdCookie(studyResult, null, null);
    }

    /**
     * Generates an ID cookie from the given parameters and sets it in the response object.
     */
    public void writeIdCookie(StudyResult studyResult, ComponentResult componentResult) {
        writeIdCookie(studyResult, componentResult, null);
    }

    /**
     * Generates an ID cookie from the given parameters and sets it in the response object.
     */
    public void writeIdCookie(StudyResult studyResult, JatosRun jatosRun) {
        writeIdCookie(studyResult, null, jatosRun);
    }

    /**
     * Generates an ID cookie from the given parameters and stores it in the request-scoped ID cookie collection.
     */
    public void writeIdCookie(StudyResult studyResult, ComponentResult componentResult, JatosRun jatosRun) {
        IdCookieModel newIdCookie = new IdCookieModel(studyResult, componentResult, jatosRun);
        idCookies().put(newIdCookie);
    }

    /**
     * Removes the ID cookie with the given study result ID.
     */
    public void discardIdCookie(Long studyResultId) {
        IdCookieModel idCookie = idCookies().findWithStudyResultId(studyResultId);
        if (idCookie != null) {
            idCookies().remove(idCookie);
        }
    }

    /**
     * Returns true if the max number of ID cookies has been reached and false otherwise.
     */
    public boolean maxIdCookiesReached() {
        return idCookies().isFull();
    }

    /**
     * Checks the creation time of each ID cookie in the given IdCookieCollection and returns the oldest one. Returns
     * null if the IdCookieCollection is empty.
     */
    public IdCookieModel getOldestIdCookie() {
        IdCookieCollection idCookieCollection = Http.Context.current().args().get(IDCOOKIES_TYPED_KEY);
        long oldest = Long.MAX_VALUE;
        IdCookieModel oldestIdCookie = null;
        for (IdCookieModel idCookie : idCookieCollection.getAll()) {
            Long creationTime = idCookie.getCreationTime();
            if (creationTime != null && creationTime < oldest) {
                oldest = creationTime;
                oldestIdCookie = idCookie;
            }
        }
        return oldestIdCookie;
    }

    /**
     * Checks the creation time of each ID cookie in the given IdCookieCollection and returns the study result ID of the
     * oldest one. Returns null if the IdCookieCollection is empty.
     */
    public Long getStudyResultIdOfOldestIdCookie() {
        IdCookieModel oldest = getOldestIdCookie();
        return (oldest != null) ? oldest.getStudyResultId() : null;
    }

    /*
     * Returns the JatosRun object of the ID cookie with the given study result ID. If the ID cookie doesn't belong to a JatosWorker it returns null.
     */
    public JatosRun getJatosRun(Long studyResultId) {
        return getIdCookie(studyResultId).getJatosRun();
    }

    private Set<String> extractIdCookieNames(Cookies cookies) {
        Set<String> idCookieNames = new HashSet<>();
        for (Cookie cookie : cookies) {
            if (isIdCookieName(cookie.name())) {
                idCookieNames.add(cookie.name());
            }
        }
        return idCookieNames;
    }

    private boolean isIdCookieName(String cookieName) {
        return cookieName != null
                && cookieName.toLowerCase().startsWith(IdCookieModel.ID_COOKIE_NAME.toLowerCase());
    }

    /**
     * Extracts all ID cookies from all the HTTP cookies and stores them into an {@link IdCookieCollection}. If a cookie
     * is malformed, it is discarded right away (removed from the Response).
     */
    private IdCookieCollection extractFromCookies(Cookies cookies) {
        IdCookieCollection idCookieCollection = new IdCookieCollection();
        for (Cookie cookie : cookies) {
            // Cookie names are case-insensitive
            if (cookie.name().toLowerCase().startsWith(IdCookieModel.ID_COOKIE_NAME.toLowerCase())) {
                try {
                    IdCookieModel idCookie = buildIdCookie(cookie);
                    idCookieCollection.add(idCookie);
                } catch (IdCookieMalformedException e) {
                    // If ID cookie is malformed, do not add it to the collection and continue
                    LOGGER.warn("Malformed JATOS ID cookie: " + e.getMessage());
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

    private Cookie[] generatePlayCookies() {
        IdCookieCollection cookies = Http.Context.current().args().get(IDCOOKIES_TYPED_KEY);
        return cookies.getAll().stream()
                .map(this::generatePlayCookie)
                .toArray(Cookie[]::new);
    }

    private Set<String> generatePlayCookieNames() {
        IdCookieCollection cookies = Http.Context.current().args().get(IDCOOKIES_TYPED_KEY);
        return cookies.getAll().stream()
                .map(IdCookieModel::getName)
                .collect(Collectors.toSet());
    }

    /**
     * Generates a Play cookie from the given IdCookieModel.
     */
    private Cookie generatePlayCookie(IdCookieModel idCookie) {
        String cookieValue = idCookieSerialiser.asCookieValueString(idCookie);
        play.mvc.Http.CookieBuilder cookieBuilder = builder(idCookie.getName(), cookieValue)
                .withMaxAge(Duration.of(10000, ChronoUnit.DAYS))
                .withHttpOnly(false)
                .withPath(Common.getJatosUrlBasePath())
                .withSecure(Common.isIdCookiesSecure());
        // https://github.com/JATOS/JATOS/issues/208
        // https://github.com/JATOS/JATOS/issues/231
        if (Common.getIdCookiesSameSite() != null) cookieBuilder.withSameSite(Common.getIdCookiesSameSite());
        return cookieBuilder.build();
    }

    private Cookie[] generateDiscardCookies(Set<String> cookieNames) {
        return cookieNames.stream()
                .map(cookieName -> generateDiscardCookie(cookieName, Common.getJatosUrlBasePath()))
                .toArray(Cookie[]::new);
    }

    private Cookie generateDiscardCookie(String cookieName, String path) {
        return new play.api.mvc.DiscardingCookie(
                cookieName,
                path,
                scala.Option.empty(),
                Common.isIdCookiesSecure()
        ).toCookie().asJava();
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
     * Returns the index of the ID cookie which is the suffix of its name. If the suffix is not a number, then an
     * IdCookieMalformedException is thrown.
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
            valueStr = HttpUtils.urlDecode(valueStr);
        } catch (Exception e) {
            throw new IdCookieMalformedException(PublixErrorMessages.couldntExtractFromIdCookie(cookieName, key));
        }
        if (strict && valueStr.trim().isEmpty()) {
            throw new IdCookieMalformedException(PublixErrorMessages.couldntExtractFromIdCookie(cookieName, key));
        }
        return valueStr;
    }

    /**
     * Searches the given map for the given key and returns the corresponding value as Long. Does some simple
     * validation.
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
     * Searches the given map the given key and returns the corresponding value as Integer. Does some simple
     * validation.
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

}
