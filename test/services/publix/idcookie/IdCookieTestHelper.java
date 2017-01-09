package services.publix.idcookie;

import javax.inject.Inject;

import controllers.publix.workers.JatosPublix.JatosRun;
import models.common.workers.JatosWorker;
import play.mvc.Http.Cookie;

/**
 * @author Kristian Lange (2016)
 */
public class IdCookieTestHelper {

	private final IdCookieSerialiser idCookieSerialiser;

	@Inject
	public IdCookieTestHelper(IdCookieSerialiser idCookieSerialiser) {
		this.idCookieSerialiser = idCookieSerialiser;
	}

	public IdCookie buildDummyIdCookie() {
		IdCookie idCookie = new IdCookie();
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

	public IdCookie buildDummyIdCookie(Long studyResultId) {
		IdCookie idCookie = buildDummyIdCookie();
		idCookie.setStudyResultId(studyResultId);
		return idCookie;
	}

	public Cookie buildCookie(IdCookie idCookie) {
		String cookieValue = idCookieSerialiser.asCookieValueString(idCookie);
		Cookie cookie = new Cookie(idCookie.getName(), cookieValue,
				Integer.MAX_VALUE, "/", "", false, false);
		return cookie;
	}

}
