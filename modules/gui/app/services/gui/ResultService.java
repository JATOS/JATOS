package services.gui;

import akka.actor.ActorRef;
import akka.util.ByteString;
import com.fasterxml.jackson.databind.JsonNode;
import daos.common.ComponentResultDao;
import daos.common.StudyResultDao;
import exceptions.gui.NotFoundException;
import general.common.Common;
import general.common.MessagesStrings;
import models.common.*;
import models.common.workers.Worker;
import play.db.jpa.JPAApi;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service class around ComponentResults and StudyResults. It's used by controllers or other services.
 *
 * @author Kristian Lange
 */
@Singleton
public class ResultService {

    private final ComponentResultDao componentResultDao;
    private final StudyResultDao studyResultDao;
    private final JsonUtils jsonUtils;
    private final JPAApi jpaApi;

    @Inject
    ResultService(ComponentResultDao componentResultDao, StudyResultDao studyResultDao, JsonUtils jsonUtils,
            JPAApi jpaApi) {
        this.componentResultDao = componentResultDao;
        this.studyResultDao = studyResultDao;
        this.jsonUtils = jsonUtils;
        this.jpaApi = jpaApi;
    }

    /**
     * Gets the corresponding ComponentResult for a list of IDs. Throws an exception if the ComponentResult doesn't
     * exist.
     */
    public List<ComponentResult> getComponentResults(List<Long> componentResultIdList) throws NotFoundException {
        List<ComponentResult> componentResultList = new ArrayList<>();
        for (Long componentResultId : componentResultIdList) {
            ComponentResult componentResult = componentResultDao.findById(componentResultId);
            if (componentResult == null) {
                throw new NotFoundException(MessagesStrings.componentResultNotExist(componentResultId));
            }
            componentResultList.add(componentResult);
        }
        return componentResultList;
    }

    /**
     * Get all StudyResults or throw an Exception if one doesn't exist. Throws an exception if the StudyResult doesn't
     * exist.
     */
    public List<StudyResult> getStudyResults(List<Long> studyResultIdList) throws NotFoundException {
        List<StudyResult> studyResultList = new ArrayList<>();
        for (Long studyResultId : studyResultIdList) {
            StudyResult studyResult = studyResultDao.findById(studyResultId);
            if (studyResult == null) {
                throw new NotFoundException(MessagesStrings.studyResultNotExist(studyResultId));
            }
            studyResultList.add(studyResult);
        }
        return studyResultList;
    }

    public void fetchStudyResultsByStudyPaginatedAndWriteIntoActor(ActorRef sourceActor, Study study) {
        int maxDbQuerySize = Common.getMaxResultsDbQuerySize();
        int resultCount = jpaApi.withTransaction(entityManager -> {
            return studyResultDao.countByStudy(study);
        });

        for (int i = 0; i < resultCount; i += maxDbQuerySize) {
            int first = i;
            boolean isLastPage = (first + maxDbQuerySize) >= resultCount;
            jpaApi.withTransaction(entityManager -> {
                List<StudyResult> resultList = studyResultDao.findAllByStudy(study, first, maxDbQuerySize);
                writeStudyResultsIntoActor(sourceActor, isLastPage, resultList);
            });
        }
    }

    public void fetchStudyResultsByBatchPaginatedAndWriteIntoActor(ActorRef sourceActor, Batch batch) {
        int maxDbQuerySize = Common.getMaxResultsDbQuerySize();
        int resultCount = jpaApi.withTransaction(entityManager -> {
            return studyResultDao.countByBatch(batch);
        });

        for (int i = 0; i < resultCount; i += maxDbQuerySize) {
            int first = i;
            boolean isLastPage = (first + maxDbQuerySize) >= resultCount;
            jpaApi.withTransaction(entityManager -> {
                List<StudyResult> resultList = studyResultDao.findAllByBatch(batch, first, maxDbQuerySize);
                writeStudyResultsIntoActor(sourceActor, isLastPage, resultList);
            });
        }
    }

    public void fetchStudyResultsByBatchAndWorkerTypePaginatedAndWriteIntoActor(ActorRef sourceActor, Batch batch,
            String workerType) {
        int maxDbQuerySize = Common.getMaxResultsDbQuerySize();
        int resultCount = jpaApi.withTransaction(entityManager -> {
            return studyResultDao.countByBatchAndWorkerType(batch, workerType);
        });

        for (int i = 0; i < resultCount; i += maxDbQuerySize) {
            int first = i;
            boolean isLastPage = (i + maxDbQuerySize) >= resultCount;
            jpaApi.withTransaction(entityManager -> {
                List<StudyResult> resultList = studyResultDao
                        .findAllByBatchAndWorkerType(batch, workerType, first, maxDbQuerySize);
                writeStudyResultsIntoActor(sourceActor, isLastPage, resultList);
            });
        }
    }

    public void fetchStudyResultsByGroupPaginatedAndWriteIntoActor(ActorRef sourceActor, GroupResult group) {
        int maxDbQuerySize = Common.getMaxResultsDbQuerySize();
        int resultCount = jpaApi.withTransaction(entityManager -> {
            return studyResultDao.countByGroup(group);
        });

        for (int i = 0; i < resultCount; i += maxDbQuerySize) {
            int first = i;
            boolean isLastPage = (i + maxDbQuerySize) >= resultCount;
            jpaApi.withTransaction(entityManager -> {
                List<StudyResult> resultList = studyResultDao.findAllByGroup(group, first, maxDbQuerySize);
                writeStudyResultsIntoActor(sourceActor, isLastPage, resultList);
            });
        }
    }

    public void fetchStudyResultsByWorkerPaginatedAndWriteIntoActor(ActorRef sourceActor, Worker worker, User user) {
        int maxDbQuerySize = Common.getMaxResultsDbQuerySize();
        int resultCount = jpaApi.withTransaction(entityManager -> {
            return studyResultDao.countByWorker(worker, user);
        });

        for (int i = 0; i < resultCount; i += maxDbQuerySize) {
            int first = i;
            boolean isLastPage = (i + maxDbQuerySize) >= resultCount;
            jpaApi.withTransaction(entityManager -> {
                List<StudyResult> resultList = studyResultDao.findAllByWorker(worker, user, first, maxDbQuerySize);
                writeStudyResultsIntoActor(sourceActor, isLastPage, resultList);
            });
        }
    }

    private void writeStudyResultsIntoActor(ActorRef sourceActor, boolean isLastPage, List<StudyResult> resultList) {
        for (int i = 0; i < resultList.size(); i++) {
            StudyResult result = resultList.get(i);
            int componentResultCount = componentResultDao.countByStudyResult(result);
            JsonNode resultNode = jsonUtils.studyResultAsJsonNode(result, componentResultCount);
            sourceActor.tell(ByteString.fromString(resultNode.toString()), ActorRef.noSender());
            boolean isLastResult = (i + 1) >= resultList.size();
            if (!isLastPage || !isLastResult) {
                sourceActor.tell(ByteString.fromString(",\n"), ActorRef.noSender());
            }
        }
    }

    /**
     * Retrieves ComponentResult (including their result data) and uses the given Supplier function to fetches them
     * from the database. It gets up to max results - or if max is not defined it gets all. It also checks the
     * ComponentResult.
     */
    public void fetchComponentResultsPaginatedAndWriteIntoActor(ActorRef sourceActor, Component component) {
        int maxDbQuerySize = Common.getMaxResultsDbQuerySize();
        int resultCount = jpaApi.withTransaction(entityManager -> {
            return componentResultDao.countByComponent(component);
        });

        for (int i = 0; i < resultCount; i += maxDbQuerySize) {
            int first = i;
            boolean isLastPage = (i + maxDbQuerySize) >= resultCount;
            jpaApi.withTransaction(entityManager -> {
                List<ComponentResult> resultList = componentResultDao
                        .findAllByComponent(component, first, maxDbQuerySize);
                writeComponentResultIntoActor(sourceActor, isLastPage, resultList);
            });
        }
    }

    private void writeComponentResultIntoActor(ActorRef sourceActor, boolean isLastPage,
            List<ComponentResult> resultList) {
        for (int j = 0; j < resultList.size(); j++) {
            ComponentResult result = resultList.get(j);
            JsonNode resultNode = jsonUtils.componentResultAsJsonNode(result, true);
            sourceActor.tell(ByteString.fromString(resultNode.toString()), ActorRef.noSender());
            boolean isLastResult = (j + 1) >= resultList.size();
            if (!isLastPage || !isLastResult) {
                sourceActor.tell(ByteString.fromString(",\n"), ActorRef.noSender());
            }
        }
    }

    /**
     * Returns the last 5 finished and unfinished StudyResultStatus
     */
    public Map<String, Object> getStudyResultStatus() {
        Map<String, Object> studyResultStatus = new HashMap<>();

        List<StudyResultStatus> lastUnfinishedStudyResults = studyResultDao.findLastUnfinished(5);
        fillUsers(lastUnfinishedStudyResults);
        studyResultStatus.put("lastUnfinishedStudyResults", lastUnfinishedStudyResults);

        List<StudyResultStatus> lastFinishedStudyResults = studyResultDao.findLastFinished(5);
        fillUsers(lastFinishedStudyResults);
        studyResultStatus.put("lastFinishedStudyResults", lastFinishedStudyResults);

        return studyResultStatus;
    }

    /**
     * Adds the user's name and username to the given list of StudyResultStatus
     */
    private void fillUsers(List<StudyResultStatus> lastUnfinishedStudyResults) {
        for (StudyResultStatus srs : lastUnfinishedStudyResults) {
            for (User user : srs.getStudy().getUserList()) {
                srs.addUser(user.getName() + " (" + user.getUsername() + ")");
            }
        }
    }

}
