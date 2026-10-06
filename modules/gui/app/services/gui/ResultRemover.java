package services.gui;

import daos.common.ComponentResultDao;
import daos.common.GroupResultDao;
import daos.common.StudyResultDao;
import general.common.StudyLogger;
import http.common.Http.Context;
import models.common.*;
import utils.common.IOUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static auth.gui.AuthAction.SIGNEDIN_USER;

/**
 * Service class that removes ComponentResults or StudyResults. It's used by controllers or other services.
 */
@Singleton
public class ResultRemover {

    private final AuthorizationService authorizationService;
    private final ComponentResultDao componentResultDao;
    private final StudyResultDao studyResultDao;
    private final GroupResultDao groupResultDao;
    private final StudyLogger studyLogger;
    private final IOUtils ioUtils;

    @Inject
    ResultRemover(AuthorizationService authorizationService,
                  ComponentResultDao componentResultDao,
                  StudyResultDao studyResultDao,
                  GroupResultDao groupResultDao,
                  StudyLogger studyLogger,
                  IOUtils ioUtils) {
        this.authorizationService = authorizationService;
        this.componentResultDao = componentResultDao;
        this.studyResultDao = studyResultDao;
        this.groupResultDao = groupResultDao;
        this.studyLogger = studyLogger;
        this.ioUtils = ioUtils;
    }

    /**
     * Retrieves all ComponentResults that correspond to the IDs in the given String, checks them and if yes, removes
     * them. Ignores IDs that do not point to a result. Removes result upload files.
     *
     * @param componentResultIdList List of IDs of ComponentResults
     */
    public void removeComponentResults(List<Long> componentResultIdList, boolean removeEmptyStudyResults) {
        studyResultDao.withTransaction(em -> {
            User signedinUser = Context.current().args().get(SIGNEDIN_USER);
            List<ComponentResult> componentResultList = componentResultDao.findByIds(componentResultIdList);
            authorizationService.canUserAccessComponentResults(componentResultList, signedinUser, true);
            for (ComponentResult componentResult : componentResultList) {
                removeComponentResult(componentResult.getId());
                if (removeEmptyStudyResults && componentResult.getStudyResult().getComponentResultList().isEmpty()) {
                    removeEmptyStudyResult(componentResult.getStudyResult());
                }
            }

            Set<Study> studies = new HashSet<>();
            componentResultList.forEach(cr -> studies.add(cr.getStudyResult().getStudy()));
            studies.forEach(study -> studyLogger.log(study, signedinUser, "Removed result data and files"));
        });
    }

    /**
     * Retrieves all StudyResults that correspond to the IDs in the given String, checks if the given user is allowed to
     * remove them and if yes, removes them. Removes result upload files.
     *
     * @param studyResultIdList List of IDs of StudyResults.
     */
    public void removeStudyResults(List<Long> studyResultIdList) {
        List<StudyResult> studyResultList = studyResultDao.findByIds(studyResultIdList);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        Set<Study> studies = new HashSet<>();
        authorizationService.canUserAccessStudyResults(studyResultList, signedinUser, true);
        for (StudyResult studyResult : studyResultList) {
            removeStudyResult(studyResult.getId());
        }
        studyResultList.forEach(sr -> studies.add(sr.getStudy()));
        studies.forEach(study -> studyLogger.log(study, signedinUser, "Removed result data and files"));
    }

    /**
     * Removes all ComponentResults that belong to the given component. Remove them from their StudyResults. Removes
     * result upload files.
     */
    void removeAllComponentResults(Component component) {
        componentResultDao.withTransaction(em -> {
            List<ComponentResult> componentResultList = componentResultDao.findAllByComponent(component);
            for (ComponentResult componentResult : componentResultList) {
                removeComponentResult(componentResult.getId());
            }
            User signedinUser = Context.current().args().get(SIGNEDIN_USER);
            studyLogger.log(component.getStudy(), signedinUser, "Removed result data and files");
        });
    }

    /**
     * Removes and flushes all StudyResults before deleting their batch. Removes upload files after commit.
     */
    void removeAllStudyResults(Batch batch) {
        studyResultDao.withTransaction(em -> {
            List<StudyResult> studyResultList = studyResultDao.findAllByBatch(batch);
            for (StudyResult studyResult : studyResultList) {
                removeStudyResultAndUploadDir(studyResult);
            }
            // Establish deletion order: first child deletion before Hibernate queues Batch/Study deletion and its database cascades.
            em.flush();
            User signedinUser = Context.current().args().get(SIGNEDIN_USER);
            Study study = batch.getStudy();
            studyResultDao.afterCommit("Log result removal for batch " + batch.getId(),
                    () -> studyLogger.log(study, signedinUser, "Removed result data and files"));
        });
    }

    /**
     * Removes and flushes all StudyResults before deleting their study. Removes upload files after commit.
     */
    void removeAllStudyResults(Study study, User user) {
        studyResultDao.withTransaction(em -> {
            for (Long id : studyResultDao.findIdsByStudyId(study.getId())) {
                removeStudyResultAndUploadDir(studyResultDao.findById(id));
            }
            // Establish deletion order: first child deletion before Hibernate queues Batch/Study deletion and its database cascades.
            em.flush();
            studyResultDao.afterCommit("Log result removal for study " + study.getId(),
                    () -> studyLogger.log(study, user, "Removed result data and files"));
        });
    }

    /**
     * Only for whole-study/batch deletion: the parent cascade will remove the groups. Do not queue group removal here,
     * since group rows still have other referencing results.
     */
    private void removeStudyResultAndUploadDir(StudyResult result) {
        Long id = result.getId();
        studyResultDao.remove(result); // Hibernate removes component results before their study result.
        studyResultDao.afterCommit("Remove uploads for study result " + id, () -> ioUtils.removeResultUploadsDir(id));
    }

    /**
     * Remove ComponentResult from its StudyResult and then remove itself. Removes result upload files.
     */
    private void removeComponentResult(long componentResultId) {
        componentResultDao.withTransaction(em -> {
            ComponentResult componentResult = componentResultDao.findById(componentResultId);
            StudyResult studyResult = componentResult.getStudyResult();
            if (studyResult != null) {
                studyResult.removeComponentResult(componentResult);
                Long studyResultId = studyResult.getId();
                Long resultId = componentResult.getId();
                studyResultDao.afterCommit("Remove uploads for component result " + resultId,
                        () -> ioUtils.removeResultUploadsDir(studyResultId, resultId));
            }
            componentResultDao.remove(componentResult);
        });
    }

    /**
     * Removes all ComponentResults of the given StudyResult and then removes the StudyResult itself. ComponentResults
     * will cascade via database constraints. Removes result upload files.
     */
    private void removeStudyResult(long studyResultId) {
        studyResultDao.withTransaction(em -> {
            StudyResult studyResult = studyResultDao.findById(studyResultId);

            removeEmptyStudyResult(studyResult);
        });
    }

    private void removeEmptyStudyResult(StudyResult studyResult) {
        studyResultDao.withTransaction(entityManager -> {
            // Remove studyResult as a member from a group result
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

            Long id = studyResult.getId();
            studyResultDao.afterCommit("Remove uploads for study result " + id,
                    () -> ioUtils.removeResultUploadsDir(id));

            // Remove studyResult (Worker cleanup is handled by database cascade)
            studyResultDao.remove(studyResult);
        });
    }

    /**
     * If the group has no more members, remove it.
     */
    private void updateOrRemoveGroupResult(GroupResult groupResult) {
        if (groupResult.getGroupState() == GroupResult.GroupState.FINISHED &&
                groupResult.getActiveMemberCount() == 0 && groupResult.getHistoryMemberCount() == 0) {
            groupResultDao.remove(groupResult);
        } else {
            groupResultDao.merge(groupResult);
        }
    }

}
