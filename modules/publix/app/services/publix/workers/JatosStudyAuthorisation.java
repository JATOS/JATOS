package services.publix.workers;

import controllers.publix.workers.JatosPublix;
import exceptions.publix.ForbiddenPublixException;
import models.common.Batch;
import models.common.Study;
import models.common.User;
import models.common.workers.JatosWorker;
import models.common.workers.Worker;
import play.mvc.Http;
import services.publix.PublixErrorMessages;
import services.publix.StudyAuthorisation;
import utils.common.Helpers;

import javax.inject.Singleton;

/**
 * StudyAuthorization for JatosWorker
 *
 * @author Kristian Lange
 */
@Singleton
public class JatosStudyAuthorisation extends StudyAuthorisation {

    @Override
    public void checkWorkerAllowedToStartStudy(Http.Session session, Worker worker, Study study, Batch batch)
            throws ForbiddenPublixException {
        if (!study.isActive()) {
            throw new ForbiddenPublixException(PublixErrorMessages.studyDeactivated(study.getId()));
        }
        if (!batch.isActive()) {
            throw new ForbiddenPublixException(PublixErrorMessages.batchInactive(batch.getId()));
        }
        checkMaxTotalWorkers(batch, worker);
        checkWorkerAllowedToDoStudy(session, worker, study, batch);
    }

    @Override
    public void checkWorkerAllowedToDoStudy(Http.Session session, Worker worker, Study study, Batch batch)
            throws ForbiddenPublixException {
        // Do not check for worker type - Jatos worker is always allowed
        User user = ((JatosWorker) worker).getUser();
        // User has to be a member user of this study
        if (!(study.hasUser(user) || Helpers.isAllowedSuperuser(user))) {
            throw new ForbiddenPublixException(PublixErrorMessages.workerNotAllowedStudy(worker, study.getId()));
        }
        // User has to be logged in
        String username = session.getOrDefault(JatosPublix.SESSION_USERNAME, "");
        if (!user.getUsername().equals(username)) {
            throw new ForbiddenPublixException(PublixErrorMessages.workerNotAllowedStudy(worker, study.getId()));
        }
    }

}
