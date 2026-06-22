package services.publix.workers;

import controllers.publix.workers.JatosPublix;
import exceptions.common.ForbiddenException;
import general.common.Common;
import http.common.Http.Context;
import models.common.Batch;
import models.common.Study;
import models.common.User;
import models.common.workers.JatosWorker;
import models.common.workers.Worker;
import services.publix.PublixErrorMessages;
import services.publix.StudyAuthorisation;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * StudyAuthorization for JatosWorker
 */
@Singleton
public class JatosStudyAuthorisation extends StudyAuthorisation {

    @Override
    public void checkWorkerAllowedToStartStudy(Worker worker, Study study, Batch batch) {
        if (!study.isActive()) {
            throw new ForbiddenException(PublixErrorMessages.studyDeactivated(study.getId()));
        }
        if (!batch.isActive()) {
            throw new ForbiddenException(PublixErrorMessages.batchInactive(batch.getId()));
        }
        checkMaxTotalWorkers(batch);
        checkWorkerAllowedToDoStudy(worker, study, batch);
    }

    @Override
    public void checkWorkerAllowedToDoStudy(Worker worker, Study study, Batch batch) {
        // Do not check for worker type - Jatos worker is always allowed
        User user = ((JatosWorker) worker).getUser();
        // User has to be a member user of this study
        boolean isSuperuser = Common.isUserRoleAllowSuperuser() && user.isSuperuser();
        if (!(study.hasUser(user) || isSuperuser)) {
            throw new ForbiddenException(PublixErrorMessages.workerNotAllowedStudy(worker, study.getId()));
        }
        // User has to be signed in
        Optional<String> username = Context.current().response().session().get(JatosPublix.SESSION_USERNAME);
        if (username.isEmpty() || !user.getUsername().equals(username.get())) {
            throw new ForbiddenException(PublixErrorMessages.workerNotAllowedStudy(worker, study.getId()));
        }
    }

}
