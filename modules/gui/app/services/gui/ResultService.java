package services.gui;

import akka.actor.ActorRef;
import akka.util.ByteString;
import com.fasterxml.jackson.databind.JsonNode;
import daos.common.ComponentResultDao;
import daos.common.StudyResultDao;
import exceptions.gui.NotFoundException;
import general.common.MessagesStrings;
import models.common.ComponentResult;
import models.common.StudyResult;
import models.common.User;
import models.common.workers.Worker;
import org.hibernate.ScrollableResults;
import play.Logger;
import play.db.jpa.JPAApi;
import scala.Option;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Service class around ComponentResults and StudyResults. It's used by controllers or other services.
 *
 * @author Kristian Lange
 */
@Singleton
public class ResultService {

    private static final Logger.ALogger LOGGER = Logger.of(ResultService.class);

    private final ComponentResultDao componentResultDao;
    private final StudyResultDao studyResultDao;
    private final Checker checker;
    private final JsonUtils jsonUtils;
    private final JPAApi jpaApi;

    @Inject
    ResultService(ComponentResultDao componentResultDao, StudyResultDao studyResultDao, Checker checker,
            JsonUtils jsonUtils, JPAApi jpaApi) {
        this.componentResultDao = componentResultDao;
        this.studyResultDao = studyResultDao;
        this.checker = checker;
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

    /**
     * Generate the list of StudyResults that belong to the given Worker and that the given user is allowed to see. A
     * user is allowed if the study that the StudyResult belongs too has this user.
     */
    public List<StudyResult> getAllowedStudyResultList(User user, Worker worker) {
        // Check for studyResult != null should not be necessary but it lead to an NPE at least once
        return worker.getStudyResultList().stream().filter(
                studyResult -> studyResult != null && studyResult.getStudy().hasUser(user)).collect(
                Collectors.toList());
    }

    /**
     * Retrieves StudyResults (including their result data) and uses the given Supplier function to fetches them from
     * the database. It gets up to max results -  or if max is not defined it gets all. It also checks the StudyResult.
     */
    public void fetchStudyResultsAndWriteIntoActor(ActorRef sourceActor, User user, Option<Integer> max,
            Supplier<ScrollableResults> resultFetcher) {
        jpaApi.withTransaction(entityManager -> {
            ScrollableResults results = resultFetcher.get();
            int i = 0;
            while (results.next() && (max.isEmpty() || i < max.get())) {
                try {
                    StudyResult result = (StudyResult) results.get(0);
                    checker.checkStudyResult(result, user, false);
                    JsonNode resultNode = jsonUtils.studyResultAsJsonNode(result);
                    sourceActor.tell(ByteString.fromString(resultNode.toString()), ActorRef.noSender());
                    i++;
                    if (!results.isLast() && (max.isEmpty() || i < max.get())) {
                        sourceActor.tell(ByteString.fromString(",\n"), ActorRef.noSender());
                    }
                } catch (Exception e) {
                    LOGGER.warn("Couldn't get result");
                }
            }
        });
    }

    /**
     * Retrieves ComponentResult (including their result data) and uses the given Supplier function to fetches them from
     * the database. It gets up to max results - or if max is not defined it gets all. It also checks the ComponentResult.
     */
    public void fetchComponentResultsAndWriteIntoActor(ActorRef sourceActor, User user, Option<Integer> max,
            Supplier<ScrollableResults> resultFetcher) {
        jpaApi.withTransaction(entityManager -> {
            ScrollableResults results = resultFetcher.get();
            int i = 0;
            while (results.next() && (max.isEmpty() || i < max.get())) {
                try {
                    ComponentResult result = (ComponentResult) results.get(0);
                    checker.checkComponentResult(result, user, false);
                    JsonNode resultNode = jsonUtils.componentResultAsJsonNode(result);
                    sourceActor.tell(ByteString.fromString(resultNode.toString()), ActorRef.noSender());
                    i++;
                    if (!results.isLast() && (max.isEmpty() || i < max.get())) {
                        sourceActor.tell(ByteString.fromString(",\n"), ActorRef.noSender());
                    }
                } catch (Exception e) {
                    LOGGER.warn("Couldn't get result");
                }
            }
        });
    }

}
