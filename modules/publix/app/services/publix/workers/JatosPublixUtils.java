package services.publix.workers;

import controllers.publix.Publix;
import controllers.publix.workers.JatosPublix;
import controllers.publix.workers.JatosPublix.JatosRun;
import daos.common.*;
import daos.common.worker.WorkerDao;
import exceptions.publix.BadRequestPublixException;
import exceptions.publix.ForbiddenPublixException;
import general.common.StudyLogger;
import group.GroupAdministration;
import models.common.User;
import models.common.workers.JatosWorker;
import models.common.workers.Worker;
import play.mvc.Http;
import services.publix.PublixUtils;
import services.publix.ResultCreator;
import services.publix.idcookie.IdCookieService;
import utils.common.IOUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

/**
 * JatosPublix' implementation of PublixUtils (studies or components started via
 * JATOS' UI).
 *
 * @author Kristian Lange
 */
@Singleton
public class JatosPublixUtils extends PublixUtils<JatosWorker> {

    private final JatosErrorMessages errorMessages;
    private final UserDao userDao;

    @Inject
    JatosPublixUtils(ResultCreator resultCreator,
            IdCookieService idCookieService,
            GroupAdministration groupAdministration,
            JatosErrorMessages errorMessages, UserDao userDao,
            StudyDao studyDao, StudyResultDao studyResultDao,
            ComponentDao componentDao, ComponentResultDao componentResultDao,
            WorkerDao workerDao, BatchDao batchDao, StudyLogger studyLogger, IOUtils ioUtils) {
        super(resultCreator, idCookieService, groupAdministration,
                errorMessages, studyDao, studyResultDao, componentDao,
                componentResultDao, workerDao, batchDao, studyLogger, ioUtils);
        this.errorMessages = errorMessages;
        this.userDao = userDao;
    }

    @Override
    public JatosWorker retrieveTypedWorker(Long workerId)
            throws ForbiddenPublixException {
        Worker worker = super.retrieveWorker(workerId);
        if (!(worker instanceof JatosWorker)) {
            throw new ForbiddenPublixException(
                    errorMessages.workerNotCorrectType(worker.getId()));
        }
        return (JatosWorker) worker;
    }

    /**
     * Retrieves the currently logged-in user or throws an
     * ForbiddenPublixException if none is logged-in.
     */
    public User retrieveLoggedInUser() throws ForbiddenPublixException {
        String email = Publix.session(JatosPublix.SESSION_USER_EMAIL);
        if (email == null) {
            throw new ForbiddenPublixException(
                    JatosErrorMessages.NO_USER_LOGGED_IN);
        }
        User loggedInUser = userDao.findByEmail(email);
        if (loggedInUser == null) {
            throw new ForbiddenPublixException(
                    errorMessages.userNotExist(email));
        }
        return loggedInUser;
    }

    /**
     * Retrieves the JatosRun object that maps to the jatos run parameter in the
     * session.
     */
    public JatosRun retrieveJatosRunFromSession()
            throws ForbiddenPublixException, BadRequestPublixException {
        String sessionValue = Publix.session(JatosPublix.SESSION_JATOS_RUN);
        if (sessionValue == null) {
            throw new ForbiddenPublixException(
                    JatosErrorMessages.STUDY_OR_COMPONENT_NEVER_STARTED_FROM_JATOS);
        }
        try {
            return JatosRun.valueOf(sessionValue);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BadRequestPublixException(
                    JatosErrorMessages.MALFORMED_JATOS_RUN_SESSION_PARAMETER);
        }
    }

    @Override
    public Map<String, String> getNonJatosUrlQueryParameters() {
        Map<String, String> queryMap = new HashMap<>();
        Http.Context.current().request().queryString().forEach((k, v) -> queryMap.put(k, v[0]));
        queryMap.remove(JatosPublix.JATOS_WORKER_ID);
        queryMap.remove("batchId");
        return queryMap;
    }

}
