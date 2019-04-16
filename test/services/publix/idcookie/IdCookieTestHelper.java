package services.publix.idcookie;

import controllers.publix.workers.JatosPublix.JatosRun;
import general.common.Common;
import models.common.workers.JatosWorker;
import play.mvc.Http.Cookie;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static org.fest.assertions.Assertions.assertThat;

/**
 * @author Kristian Lange (2016)
 */
@Singleton
public class IdCookieTestHelper {

    private final IdCookieSerialiser idCookieSerialiser;

    @Inject
    public IdCookieTestHelper(IdCookieSerialiser idCookieSerialiser) {
        this.idCookieSerialiser = idCookieSerialiser;
    }

    private IdCookieModel buildDummyIdCookie() {
        IdCookieModel idCookie = new IdCookieModel();
        idCookie.setBatchId(1L);
        idCookie.setComponentId(1L);
        idCookie.setComponentPosition(1);
        idCookie.setComponentResultId(1L);
        idCookie.setCreationTime(System.currentTimeMillis());
        idCookie.setGroupResultId(1L);
        idCookie.setIndex(0);
        idCookie.setJatosRun(JatosRun.RUN_STUDY);
        idCookie.setName("JATOS_IDS_0");
        idCookie.setStudyAssets("test_study_assets");
        idCookie.setStudyId(1L);
        idCookie.setStudyResultId(1L);
        idCookie.setWorkerId(1L);
        idCookie.setWorkerType(JatosWorker.WORKER_TYPE);
        idCookie.setUrlBasePath("/somepath/");
        return idCookie;
    }

    public IdCookieModel buildDummyIdCookie(Long studyResultId) {
        IdCookieModel idCookie = buildDummyIdCookie();
        idCookie.setStudyResultId(studyResultId);
        return idCookie;
    }

    public void checkDummyIdCookie(IdCookieModel idCookie) {
        assertThat(idCookie.getBatchId()).isEqualTo(1L);
        assertThat(idCookie.getComponentId()).isEqualTo(1L);
        assertThat(idCookie.getComponentPosition()).isEqualTo(1);
        assertThat(idCookie.getComponentResultId()).isEqualTo(1L);
        assertThat(idCookie.getCreationTime()).isGreaterThan(0L);
        assertThat(idCookie.getGroupResultId()).isEqualTo(1L);
        assertThat(idCookie.getIndex()).isEqualTo(0);
        assertThat(idCookie.getJatosRun()).isEqualTo(JatosRun.RUN_STUDY);
        assertThat(idCookie.getName()).isEqualTo("JATOS_IDS_0");
        assertThat(idCookie.getStudyAssets()).isEqualTo("test_study_assets");
        assertThat(idCookie.getStudyId()).isEqualTo(1L);
        assertThat(idCookie.getStudyResultId()).isEqualTo(1L);
        assertThat(idCookie.getWorkerId()).isEqualTo(1L);
        assertThat(idCookie.getWorkerType()).isEqualTo(JatosWorker.WORKER_TYPE);
        assertThat(idCookie.getUrlBasePath()).isEqualTo("/somepath/");
    }

    public Cookie buildCookie(IdCookieModel idCookie) {
        String cookieValue = idCookieSerialiser.asCookieValueString(idCookie);
        return Cookie.builder(idCookie.getName(), cookieValue)
                .withMaxAge(Duration.of(10000, ChronoUnit.DAYS))
                .withSecure(false)
                .withHttpOnly(false)
                .withPath(Common.getPlayHttpContext())
                .withDomain("")
                .build();
    }

}
