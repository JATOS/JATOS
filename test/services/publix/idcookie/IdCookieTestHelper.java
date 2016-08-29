package services.publix.idcookie;

import controllers.publix.workers.JatosPublix.JatosRun;
import models.common.workers.JatosWorker;

/**
 * @author Kristian Lange (2016)
 */
public class IdCookieTestHelper {

	public static IdCookie buildDummyIdCookie() {
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

	public static IdCookie buildDummyIdCookie(Long studyResultId) {
		IdCookie idCookie = buildDummyIdCookie();
		idCookie.setStudyResultId(studyResultId);
		return idCookie;
	}

}
