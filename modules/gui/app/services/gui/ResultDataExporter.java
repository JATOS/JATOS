package services.gui;

import akka.actor.ActorRef;
import akka.util.ByteString;
import daos.common.ComponentResultDao;
import daos.common.StudyResultDao;
import general.common.StudyLogger;
import models.common.ComponentResult;
import models.common.Study;
import models.common.StudyResult;
import models.common.User;
import play.Logger;
import play.db.jpa.JPAApi;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service class that streams result data to Actor sources.
 *
 * @author Kristian Lange
 */
@Singleton
public class ResultDataExporter {

    private static final Logger.ALogger LOGGER = Logger.of(ResultDataExporter.class);

    private final Checker checker;
    private final ComponentResultDao componentResultDao;
    private final StudyResultDao studyResultDao;
    private final StudyLogger studyLogger;
    private final JPAApi jpaApi;

    @Inject
    ResultDataExporter(Checker checker, ComponentResultDao componentResultDao, StudyResultDao studyResultDao,
            StudyLogger studyLogger, JPAApi jpaApi) {
        this.checker = checker;
        this.componentResultDao = componentResultDao;
        this.studyResultDao = studyResultDao;
        this.studyLogger = studyLogger;
        this.jpaApi = jpaApi;
    }

    /**
     * Retrieves the StudyResults that correspond to the IDs, checks them and returns all their result data.
     */
    public void byStudyResultIds(ActorRef sourceActor, List<Long> studyResultIdList, User user) {
        Set<Study> studies = new HashSet<>();
        for (Long studyResultId : studyResultIdList) {
            jpaApi.withTransaction(entityManager -> {
                try {
                    StudyResult studyResult = studyResultDao.findById(studyResultId);
                    if (studyResult != null) {
                        checker.checkStudyResult(studyResult, user, false);
                        studies.add(studyResult.getStudy());
                        studyResult.getComponentResultList().forEach(cr -> tellResultData(sourceActor, cr));
                    } else {
                        LOGGER.warn("A study result with ID " + studyResultId + " doesn't exist.");
                    }
                } catch (Exception e) {
                    LOGGER.warn("Couldn't get result data", e);
                }
            });
        }
        studies.forEach(study -> studyLogger.log(study, user, "Exported result data to file"));
    }

    /**
     * Retrieves the ComponentResults that correspond to the IDs, checks them and returns all their result data.
     */
    public void byComponentResultIds(ActorRef sourceActor, List<Long> componentResultIdList, User user) {
        Set<Study> studies = new HashSet<>();
        for (Long componentResultId : componentResultIdList) {
            jpaApi.withTransaction(entityManager -> {
                try {
                    ComponentResult componentResult = componentResultDao.findById(componentResultId);
                    if (componentResult != null) {
                        checker.checkComponentResult(componentResult, user, false);
                        studies.add(componentResult.getStudyResult().getStudy());
                        tellResultData(sourceActor, componentResult);
                    } else {
                        LOGGER.warn("A component result with ID " + componentResultId + " doesn't exist.");
                    }
                } catch (Exception e) {
                    LOGGER.warn("Couldn't get result data", e);
                }
            });
        }
        studies.forEach(study -> studyLogger.log(study, user, "Exported result data to file"));
    }

    private void tellResultData(ActorRef sourceActor, ComponentResult componentResult) {
        String resultDataStr = componentResult.getData();
        if (resultDataStr == null) return;
        ByteString lineToSend = ByteString.fromString(resultDataStr + System.lineSeparator());
        sourceActor.tell(lineToSend, null);
    }

}
