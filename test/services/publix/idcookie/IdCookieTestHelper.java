package services.publix.idcookie;

import static org.fest.assertions.Assertions.assertThat;

import javax.inject.Inject;
import javax.inject.Singleton;

import controllers.publix.workers.JatosPublix.JatosRun;
import models.common.workers.JatosWorker;
import play.mvc.Http.Cookie;

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

	public IdCookieModel buildDummyIdCookie() {
		IdCookieModel idCookie = new IdCookieModel();
		idCookie.setBatchId(1l);
		idCookie.setComponentId(1l);
		idCookie.setComponentPosition(1);
		idCookie.setComponentResultId(1l);
		idCookie.setCreationTime(System.currentTimeMillis());
		idCookie.setGroupResultId(1l);
		idCookie.setIndex(0);
		idCookie.setJatosRun(JatosRun.RUN_STUDY);
		idCookie.setName("JATOS_IDS_0");
		idCookie.setStudyAssets("test_study_assets");
		idCookie.setStudyId(1l);
		idCookie.setStudyResultId(1l);
		idCookie.setWorkerId(1l);
		idCookie.setWorkerType(JatosWorker.WORKER_TYPE);
		return idCookie;
	}

	public IdCookieModel buildDummyIdCookie(Long studyResultId) {
		IdCookieModel idCookie = buildDummyIdCookie();
		idCookie.setStudyResultId(studyResultId);
		return idCookie;
	}
	
	public void checkDummyIdCookie(IdCookieModel idCookie) {
		assertThat(idCookie.getBatchId()).isEqualTo(1l);
		assertThat(idCookie.getComponentId()).isEqualTo(1l);
		assertThat(idCookie.getComponentPosition()).isEqualTo(1);
		assertThat(idCookie.getComponentResultId()).isEqualTo(1l);
		assertThat(idCookie.getCreationTime()).isGreaterThan(0l);
		assertThat(idCookie.getGroupResultId()).isEqualTo(1l);
		assertThat(idCookie.getIndex()).isEqualTo(0);
		assertThat(idCookie.getJatosRun())
				.isEqualTo(JatosRun.RUN_STUDY);
		assertThat(idCookie.getName()).isEqualTo("JATOS_IDS_0");
		assertThat(idCookie.getStudyAssets())
				.isEqualTo("test_study_assets");
		assertThat(idCookie.getStudyId()).isEqualTo(1l);
		assertThat(idCookie.getStudyResultId()).isEqualTo(1l);
		assertThat(idCookie.getWorkerId()).isEqualTo(1l);
		assertThat(idCookie.getWorkerType())
				.isEqualTo(JatosWorker.WORKER_TYPE);
	}

	public Cookie buildCookie(IdCookieModel idCookie) {
		String cookieValue = idCookieSerialiser.asCookieValueString(idCookie);
		Cookie cookie = new Cookie(idCookie.getName(), cookieValue,
				Integer.MAX_VALUE, "/", "", false, false);
		return cookie;
	}

}
