package services.gui;

import daos.common.ComponentResultDao;
import daos.common.GroupResultDao;
import daos.common.StudyResultDao;
import daos.common.worker.WorkerDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.NotFoundException;
import general.common.StudyLogger;
import models.common.*;
import models.common.workers.Worker;
import play.Logger;
import play.Logger.ALogger;
import utils.common.IOUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service class that removes ComponentResults or StudyResults. It's used by
 * controllers or other services.
 *
 * @author Kristian Lange
 */
@Singleton
public class ResultRemover {

    private static final ALogger LOGGER = Logger.of(ResultRemover.class);

    private final Checker checker;
    private final ComponentResultDao componentResultDao;
    private final StudyResultDao studyResultDao;
    private final GroupResultDao groupResultDao;
    private final WorkerDao workerDao;
    private final StudyLogger studyLogger;
    private final IOUtils ioUtils;

    @Inject
    ResultRemover(Checker checker, ComponentResultDao componentResultDao,
            StudyResultDao studyResultDao, GroupResultDao groupResultDao,
            WorkerDao workerDao, StudyLogger studyLogger, IOUtils ioUtils) {
        this.checker = checker;
        this.componentResultDao = componentResultDao;
        this.studyResultDao = studyResultDao;
        this.groupResultDao = groupResultDao;
        this.workerDao = workerDao;
        this.studyLogger = studyLogger;
        this.ioUtils = ioUtils;
    }

    /**
     * Retrieves all ComponentResults that correspond to the IDs in the given
     * String, checks them and if yes, removes them. Removes result upload files.
     *
     * @param componentResultIdList List of IDs of ComponentResults
     * @param user                  For each ComponentResult it will be checked that the given
     *                              user is a user of the study that the ComponentResult belongs
     *                              too.
     */
    public void removeComponentResults(List<Long> componentResultIdList, User user, boolean removeEmptyStudyResults)
            throws BadRequestException, NotFoundException, ForbiddenException {
        List<ComponentResult> componentResultList = componentResultDao.findByIds(componentResultIdList);
        checker.checkComponentResults(componentResultList, user, true);
        for (ComponentResult componentResult : componentResultList) {
            removeComponentResult(componentResult.getId());
            if (removeEmptyStudyResults && componentResult.getStudyResult().getComponentResultList().isEmpty()) {
                removeEmptyStudyResult(componentResult.getStudyResult());
            }
        }

        Set<Study> studies = new HashSet<>();
        componentResultList.forEach(cr -> studies.add(cr.getStudyResult().getStudy()));
        studies.forEach(study -> studyLogger.log(study, user, "Removed result data and files"));
    }

    /**
     * Retrieves all StudyResults that correspond to the IDs in the given
     * String, checks if the given user is allowed to remove them and if yes,
     * removes them. Removes result upload files.
     *
     * @param studyResultIdList List of IDs of StudyResults.
     * @param user              For each StudyResult it will be checked that the given user is
     *                          a user of the study that the StudyResult belongs too.
     */
    public void removeStudyResults(List<Long> studyResultIdList, User user)
            throws BadRequestException, NotFoundException, ForbiddenException {
        List<StudyResult> studyResultList = studyResultDao.findByIds(studyResultIdList);
        Set<Study> studies = new HashSet<>();
        checker.checkStudyResults(studyResultList, user, true);
        for (StudyResult studyResult : studyResultList) {
            removeStudyResult(studyResult.getId());
        }
        studyResultList.forEach(sr -> studies.add(sr.getStudy()));
        studies.forEach(study -> studyLogger.log(study, user, "Removed result data and files"));
    }

    /**
     * Removes all ComponentResults that belong to the given component. Remove them from their
     * StudyResults. Removes result upload files.
     */
    void removeAllComponentResults(Component component, User user) {
        List<ComponentResult> componentResultList = componentResultDao.findAllByComponent(component);
        componentResultList.forEach(this::removeComponentResultFromStudyResult);
        for (ComponentResult componentResult : componentResultList) {
            removeComponentResult(componentResult.getId());
        }
        studyLogger.log(component.getStudy(), user, "Removed result data and files");
    }

    private void removeComponentResultFromStudyResult(ComponentResult componentResult) {
        StudyResult studyResult = componentResult.getStudyResult();
        if (studyResult != null) {
            studyResult.removeComponentResult(componentResult);
            studyResultDao.update(studyResult);
        } else {
            LOGGER.error(".removeComponentResult: StudyResult is null - "
                    + "but a ComponentResult always belongs to a StudyResult "
                    + "(ComponentResult's ID is " + componentResult.getId() + ")");
        }
    }

    /**
     * Removes all StudyResults that belong to the given batch. Removes result upload files.
     */
    void removeAllStudyResults(Batch batch, User user) {
        List<StudyResult> studyResultList = studyResultDao.findAllByBatch(batch);
        for (StudyResult studyResult : studyResultList) {
            removeStudyResult(studyResult.getId());
        }
        studyLogger.log(batch.getStudy(), user, "Removed result data and files");
    }

    /**
     * Remove ComponentResult from its StudyResult and then remove itself. Removes result upload files.
     */
    private void removeComponentResult(long componentResultId) {
        ComponentResult componentResult = componentResultDao.findById(componentResultId);
        StudyResult studyResult = componentResult.getStudyResult();
        if (studyResult == null) {
            LOGGER.error(".removeComponentResult: StudyResult is null - but a ComponentResult always belongs to a "
                    + "StudyResult (ComponentResult's ID is " + componentResult.getId() + ")");
            componentResultDao.remove(componentResult);
            return;
        }

        studyResult.removeComponentResult(componentResult);
        studyResultDao.update(studyResult);
        try {
            // Remove componentResult's upload dir
            ioUtils.removeResultUploadsDir(studyResult.getId(), componentResult.getId());
        } catch (IOException e) {
            LOGGER.error(".removeComponentResult: Couldn't remove upload dir " + componentResult.getId(), e);
        }
        componentResultDao.remove(componentResult);
    }

    /**
     * Removes all ComponentResults of the given StudyResult, removes this
     * StudyResult from the given worker, removes this StudyResult from the
     * GroupResult and then remove StudyResult itself. Removes result upload files.
     */
    private void removeStudyResult(long studyResultId) {
        StudyResult studyResult = studyResultDao.findById(studyResultId);

        // Remove all component results of this study result
        studyResult.getComponentResultList().forEach(componentResultDao::remove);

        removeEmptyStudyResult(studyResult);
    }

    private void removeEmptyStudyResult(StudyResult studyResult) {
        // Remove study result from worker
        Worker worker = studyResult.getWorker();
        worker.removeStudyResult(studyResult);
        workerDao.update(worker);

        // Remove studyResult as member from group result
        GroupResult activeGroupResult = studyResult.getActiveGroupResult();
        if (activeGroupResult != null) {
            activeGroupResult.removeActiveMember(studyResult);
            updateOrRemoveGroupResult(activeGroupResult);
        }
        GroupResult historyGroupResult = studyResult.getHistoryGroupResult();
        if (historyGroupResult != null) {
            historyGroupResult.removeHistoryMember(studyResult);
            updateOrRemoveGroupResult(historyGroupResult);
        }

        try {
            // Remove studyResult's upload dir
            ioUtils.removeResultUploadsDir(studyResult.getId());
        } catch (IOException e) {
            LOGGER.error(".removeStudyResult: Couldn't remove upload dir " + studyResult.getId(), e);
        }

        // Remove studyResult
        studyResultDao.remove(studyResult);
    }

    /**
     * If the group has no more members remove it.
     */
    private void updateOrRemoveGroupResult(GroupResult groupResult) {
        if (groupResult.getGroupState() == GroupResult.GroupState.FINISHED &&
                groupResult.getActiveMemberCount() == 0 && groupResult.getHistoryMemberCount() == 0) {
            groupResultDao.remove(groupResult);
        } else {
            groupResultDao.update(groupResult);
        }
    }

}
