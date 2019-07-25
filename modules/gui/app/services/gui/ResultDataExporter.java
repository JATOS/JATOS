package services.gui;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.Status;
import akka.util.ByteString;
import daos.common.ComponentDao;
import daos.common.ComponentResultDao;
import daos.common.StudyResultDao;
import daos.common.worker.WorkerDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import general.common.StudyLogger;
import models.common.*;
import models.common.workers.Worker;
import org.hibernate.ScrollableResults;
import play.Logger;
import play.db.jpa.JPAApi;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

/**
 * Service class that streams result data to Actor sources.
 *
 * @author Kristian Lange
 */
@Singleton
public class ResultDataExporter {

    private static final Logger.ALogger LOGGER = Logger.of(ResultDataExporter.class);

    private final Checker checker;
    private final ResultService resultService;
    private final ComponentResultDao componentResultDao;
    private final StudyResultDao studyResultDao;
    private final WorkerDao workerDao;
    private final ComponentDao componentDao;
    private final StudyLogger studyLogger;
    private final JPAApi jpaApi;

    @Inject
    ResultDataExporter(Checker checker, ResultService resultService, ComponentResultDao componentResultDao,
            StudyResultDao studyResultDao, WorkerDao workerDao, ComponentDao componentDao, StudyLogger studyLogger,
            JPAApi jpaApi) {
        this.checker = checker;
        this.resultService = resultService;
        this.componentResultDao = componentResultDao;
        this.studyResultDao = studyResultDao;
        this.workerDao = workerDao;
        this.componentDao = componentDao;
        this.studyLogger = studyLogger;
        this.jpaApi = jpaApi;
    }

    /**
     * Retrieves all StudyResults that belong to the given worker and that the given user is allowed to see (means
     * StudyResults from studies he is a user of), checks them and returns all their result data.
     */
    public Object getResultDataByWorker(ActorRef sourceActor, Long workerId, User user) {
        return jpaApi.withTransaction(entityManager -> {
            Worker worker = workerDao.findById(workerId);

            List<StudyResult> allowedStudyResultList = resultService.getAllowedStudyResultList(user, worker);
            for (StudyResult studyResult : allowedStudyResultList) {
                try {
                    checker.checkStudyResult(studyResult, user, false);
                    studyResult.getComponentResultList().forEach(cr -> tellResultData(sourceActor, cr));
                } catch (ForbiddenException | BadRequestException e) {
                    LOGGER.warn("Couldn't get result data", e);
                }
            }
            sourceActor.tell(new Status.Success(NotUsed.getInstance()), null);
            return NotUsed.getInstance();
        });
    }

    /**
     * Retrieves all StudyResults of the given study, checks them and returns all their result data.
     */
    public Object getResultDataByStudy(ActorRef sourceActor, Study study, User user) {
        return jpaApi.withTransaction(entityManager -> {
            ScrollableResults results = studyResultDao.findAllByStudyScrollable(study);
            while (results.next()) {
                try {
                    StudyResult studyResult = (StudyResult) results.get(0);
                    checker.checkStudyResult(studyResult, user, false);
                    studyResult.getComponentResultList().forEach(cr -> tellResultData(sourceActor, cr));
                } catch (Exception e) {
                    LOGGER.warn("Couldn't get result data", e);
                }
            }
            sourceActor.tell(new Status.Success(NotUsed.getInstance()), null);
            return NotUsed.getInstance();
        });
    }

    /**
     * Retrieves all ComponentResults of the given component, checks them and returns all their result data.
     */
    public Object getResultDataByComponent(ActorRef sourceActor, Long componentId, User user) {
        return jpaApi.withTransaction(entityManager -> {
            Component component = componentDao.findById(componentId);
            ScrollableResults results = componentResultDao.findAllByComponentScrollable(component);
            while (results.next()) {
                try {
                    ComponentResult componentResult = (ComponentResult) results.get(0);
                    checker.checkComponentResult(componentResult, user, false);
                    tellResultData(sourceActor, componentResult);
                } catch (Exception e) {
                    LOGGER.warn("Couldn't get result data", e);
                }
            }
            sourceActor.tell(new Status.Success(NotUsed.getInstance()), null);
            return NotUsed.getInstance();
        });
    }

    /**
     * Retrieves the StudyResults that correspond to the IDs, checks them and returns all their result data.
     */
    public Object getResultDataByStudyResultIds(ActorRef sourceActor, List<Long> studyResultIdList, User user) {
        return jpaApi.withTransaction(entityManager -> {
            for (Long studyResultId : studyResultIdList) {
                try {
                    StudyResult studyResult = studyResultDao.findById(studyResultId);
                    if (studyResult == null) {
                        LOGGER.warn("A study result with ID " + studyResultId + " doesn't exist.");
                        continue;
                    }
                    checker.checkStudyResult(studyResult, user, false);
                    studyResult.getComponentResultList().forEach(cr -> tellResultData(sourceActor, cr));
                } catch (Exception e) {
                    LOGGER.warn("Couldn't get result data", e);
                }
            }
            sourceActor.tell(new Status.Success(NotUsed.getInstance()), null);
            return NotUsed.getInstance();
        });
    }

    /**
     * Retrieves the ComponentResults that correspond to the IDs, checks them and returns all their result data.
     */
    public Object getResultDataByComponentResultIds(ActorRef sourceActor, List<Long> componentResultIdList, User user) {
        return jpaApi.withTransaction(entityManager -> {
            for (Long componentResultId : componentResultIdList) {
                ComponentResult componentResult = componentResultDao.findById(componentResultId);
                if (componentResult == null) {
                    LOGGER.warn("A component result with ID " + componentResultId + " doesn't exist.");
                    continue;
                }
                try {
                    checker.checkComponentResult(componentResult, user, false);
                    tellResultData(sourceActor, componentResult);
                } catch (Exception e) {
                    LOGGER.warn("Couldn't get result data", e);
                }
            }
            sourceActor.tell(new Status.Success(NotUsed.getInstance()), null);
            return NotUsed.getInstance();
        });
    }

    private void tellResultData(ActorRef sourceActor, ComponentResult componentResult) {
        String resultDataStr = componentResult.getData();
        if (resultDataStr == null) return;
        ByteString lineToSend = ByteString.fromString(resultDataStr + System.lineSeparator());
        sourceActor.tell(lineToSend, null);
        studyLogger.logResultDataExporting(componentResult);
    }

}
