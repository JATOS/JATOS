package services.publix.workers;

import controllers.publix.Publix;
import controllers.publix.workers.GeneralSinglePublix;
import general.common.Common;
import models.common.Study;
import models.common.workers.Worker;
import org.apache.commons.lang3.tuple.Pair;
import play.mvc.Http.Cookie;

import javax.inject.Singleton;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static play.mvc.Http.Cookie.SameSite;
import static play.mvc.Http.Cookie.builder;

/**
 * Manages the GeneralSingle cookie. This cookie exists only with GeneralSingle workers. In this cookie is stored which
 * studies where done in the browser where the cookie originates. This provides an easy way to check whether a
 * GeneralSingle worker tries to run the same study a second time (which is not allowed). Note, that it is easy to get
 * around this mechanism by deleting the cookie in the browser.
 *
 * A GeneralSingle cookie consists of a list of tuples storing the study ID and worker ID. With the cookie's data it is
 * possible to determine whether in this browser this study was done already with a GeneralSingle worker and by which
 * worker it was done.
 *
 * @author Kristian Lange
 */
@Singleton
public class GeneralSingleCookieService {

    public static final String COOKIE_NAME = "JATOS_GENERALSINGLE_UUIDS";

    /**
     * Delimiter in cookie's string used to separate study's UUID from worker ID
     */
    private static final String COOKIE_TUPLE_DELIMITER = "=";

    /**
     * Delimiter in cookie's string used to separate entries (tuples of study UUID and worker ID)
     */
    private static final String COOKIE_LIST_DELIMITER = "&";

    /**
     * Returns the worker ID of the GeneralSingleWorker that belongs to the given study - or null, if it doesn't exist.
     * If the study was run before the study UUID has been stored together with the worker ID in the cookie.
     */
    public Long fetchWorkerIdByStudy(Study study) {
        Cookie generalSingleCookie = Publix.request().cookies().get(COOKIE_NAME);
        if (generalSingleCookie == null) return null;

        // Get all cookie items from this study (cookie item = Pair of studyUuid and workerId)
        List<Pair<String, Long>> cookieItemsFromStudy =
                Arrays.stream(generalSingleCookie.value().split(COOKIE_LIST_DELIMITER))
                        .map(this::fetchCookieItem)
                        .filter(Objects::nonNull)
                        .filter(p -> study.getUuid().equals(p.getLeft()))
                        .collect(Collectors.toList());

        // Handle normal cases
        if (cookieItemsFromStudy.size() == 0) return null;
        if (cookieItemsFromStudy.size() == 1) return cookieItemsFromStudy.get(0).getRight();

        // Now, if we have more than one cookie item of this study, return the one with the highest workerId (this is
        // the latest). This can only happen if something went wrong or the cookie was edited by hand.
        cookieItemsFromStudy.sort(Comparator.comparing(p -> -p.getRight()));
        Pair<String, Long> latestCookieItemFromStudy = cookieItemsFromStudy.get(0);
        return latestCookieItemFromStudy.getRight();
    }

    /**
     * Returns a list of Pairs of study UUID and worker ID
     */
    private Pair<String, Long> fetchCookieItem(String cookieItemStr) {
        String[] uuidAndWorker = cookieItemStr.split(COOKIE_TUPLE_DELIMITER);
        if (uuidAndWorker.length != 2) {
            // Ignore malformed cookie values
            return null;
        }
        try {
            String studyUuid = uuidAndWorker[0];
            Long workerId = Long.valueOf(uuidAndWorker[1]);
            return Pair.of(studyUuid, workerId);
        } catch (NumberFormatException e) {
            // Ignore malformed cookie values
            return null;
        }
    }

    /**
     * Sets the cookieValue as the new GeneralSingle cookie. This cookie is HTTP only and has an expire date in the far
     * future.
     */
    public void set(String cookieValue) {
        Cookie newCookie = builder(COOKIE_NAME, cookieValue)
                .withMaxAge(Duration.of(10000, ChronoUnit.DAYS))
                .withSecure(false)
                .withHttpOnly(true)
                .withSameSite(SameSite.LAX)
                .withPath(Common.getPlayHttpContext())
                .build();
        Publix.response().setCookie(newCookie);
    }

    /**
     * Sets the cookie in the response. The cookie will contain all GeneralSingle studies done in this browser and
     * adds the given study (and worker). This cookie is HTTP only and has an expire date in the far future.
     */
    public void set(Study study, Worker worker) {
        Cookie oldCookie = Publix.response().cookie(COOKIE_NAME).orElse(
                GeneralSinglePublix.request().cookie(COOKIE_NAME));
        String newCookieValue = addStudy(study, worker, oldCookie);
        set(newCookieValue);
    }

    /**
     * If the cookie is not null it adds a new tuple (study's UUID and worker ID) to the cookie's value and returns it.
     * If the cookie is null (this browser never did a general single run) it returns the new cookie's value which is
     * just the tuple.
     */
    public String addStudy(Study study, Worker worker, Cookie generalSingleCookie) {
        if (generalSingleCookie != null) {
            return generalSingleCookie.value() + COOKIE_LIST_DELIMITER + study.getUuid() + COOKIE_TUPLE_DELIMITER
                    + worker.getId();
        } else {
            return study.getUuid() + COOKIE_TUPLE_DELIMITER + worker.getId();
        }
    }

}
