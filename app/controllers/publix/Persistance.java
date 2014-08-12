package controllers.publix;

import models.ComponentModel;
import models.StudyModel;
import models.results.ComponentResult;
import models.results.StudyResult;
import models.workers.MTSandboxWorker;
import models.workers.MTWorker;
import models.workers.Worker;

public class Persistance {

	public StudyResult createStudyResult(StudyModel study, Worker worker) {
		StudyResult studyResult = new StudyResult(study);
		studyResult.persist();
		worker.addStudyResult(studyResult);
		worker.merge();
		return studyResult;
	}

	public ComponentResult createComponentResult(StudyResult studyResult,
			ComponentModel component) {
		ComponentResult componentResult = new ComponentResult(component);
		componentResult.persist();
		studyResult.addComponentResult(componentResult);
		studyResult.merge();
		return componentResult;
	}

	public MTWorker createMTWorker(String mtWorkerId,
			boolean mTurkSandbox) {
		MTWorker worker;
		if (mTurkSandbox) {
			worker = new MTSandboxWorker(mtWorkerId);
		} else {
			worker = new MTWorker(mtWorkerId);
		}
		worker.persist();
		return worker;
	}

}
