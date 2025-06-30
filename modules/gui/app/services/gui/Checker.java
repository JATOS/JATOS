package services.gui;

import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.NotFoundException;
import general.common.MessagesStrings;
import models.common.*;
import models.common.workers.Worker;
import utils.common.Helpers;

import javax.inject.Singleton;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service class that provides checks for different entities
 *
 * @author Kristian Lange
 */
@Singleton
public class Checker {

    /**
     * Checks the component of this study and throws an Exception in case of a problem.
     */
    public void checkStandardForComponent(Long studyId, Long componentId, Component component)
            throws NotFoundException, ForbiddenException {
        if (component == null) {
            throw new NotFoundException(MessagesStrings.componentNotExist(componentId));
        }
        if (component.getStudy() == null) {
            throw new ForbiddenException(MessagesStrings.componentHasNoStudy(componentId));
        }
        // Check component belongs to the study
        if (!component.getStudy().getId().equals(studyId)) {
            throw new ForbiddenException(MessagesStrings.componentNotBelongToStudy(studyId, componentId));
        }
    }

    public void checkStandardForComponent(Long componentId, Component component, User user)
            throws NotFoundException, ForbiddenException {
        if (component == null) {
            throw new NotFoundException(MessagesStrings.componentNotExist(componentId));
        }
        if (component.getStudy() == null) {
            throw new ForbiddenException(MessagesStrings.componentHasNoStudy(componentId));
        }
        // Check that the user is a member of the study or a superuser
        Study study = component.getStudy();
        if (!(study.hasUser(user) || Helpers.isAllowedSuperuser(user))) {
            String errorMsg = MessagesStrings.studyNotUser(user.getName(), user.getUsername(), study.getId());
            throw new ForbiddenException(errorMsg);
        }
    }

    public void checkComponentBelongsToStudy(Component component, String studyIdOrUuid) throws ForbiddenException {
        Study study = component.getStudy();
        if (!study.getId().toString().equals(studyIdOrUuid) && !study.getUuid().equals(studyIdOrUuid)) {
            throw new ForbiddenException("Component does not belong to study");
        }
    }

    /**
     * Checks the batch and throws an Exception in case of a problem.
     */
    public void checkStandardForBatch(Batch batch, Study study, Long batchId)
            throws ForbiddenException, NotFoundException {
        if (batch == null) {
            String errorMsg = MessagesStrings.batchNotExist(batchId);
            throw new NotFoundException(errorMsg);
        }
        // Check that the study has this batch
        if (!study.hasBatch(batch)) {
            String errorMsg = MessagesStrings.batchNotInStudy(batchId, study.getId());
            throw new ForbiddenException(errorMsg);
        }
    }

    public void checkStandardForBatch(Batch batch, Long batchId, User user)
            throws NotFoundException, ForbiddenException {
        if (batch == null) {
            String errorMsg = MessagesStrings.batchNotExist(batchId);
            throw new NotFoundException(errorMsg);
        }
        Study study = batch.getStudy();
        if (!(study.hasUser(user) || Helpers.isAllowedSuperuser(user))) {
            String errorMsg = MessagesStrings.studyNotUser(user.getName(), user.getUsername(), study.getId());
            throw new ForbiddenException(errorMsg);
        }
    }

    /**
     * Checks the group and throws an Exception in case of a problem.
     */
    public void checkStandardForGroup(GroupResult groupResult, Study study, Long groupResultId)
            throws ForbiddenException, NotFoundException {
        if (groupResult == null) {
            String errorMsg = MessagesStrings.groupNotExist(groupResultId);
            throw new NotFoundException(errorMsg);
        }
        // Check that the group belongs to the study
        if (!groupResult.getBatch().getStudy().equals(study)) {
            String errorMsg = MessagesStrings.groupNotInStudy(groupResultId, study.getId());
            throw new ForbiddenException(errorMsg);
        }
    }

    public void checkStandardForGroup(GroupResult groupResult, Long groupResultId, User user)
            throws ForbiddenException, NotFoundException {
        if (groupResult == null) {
            String errorMsg = MessagesStrings.groupNotExist(groupResultId);
            throw new NotFoundException(errorMsg);
        }
        Study study = groupResult.getBatch().getStudy();
        if (!(study.hasUser(user) || Helpers.isAllowedSuperuser(user))) {
            String errorMsg = MessagesStrings.studyNotUser(user.getName(), user.getUsername(), study.getId());
            throw new ForbiddenException(errorMsg);
        }
    }

    /**
     * Throws an ForbiddenException if this batch is the default batch of it's study.
     */
    public void checkDefaultBatch(Batch batch) throws ForbiddenException {
        if (batch.equals(batch.getStudy().getDefaultBatch())) {
            String errorMsg = MessagesStrings.BATCH_NOT_ALLOWED_DELETE_DEFAULT;
            throw new ForbiddenException(errorMsg);
        }
    }

    /**
     * Throws an ForbiddenException if a study is locked.
     */
    public void checkStudyLocked(Study study) throws ForbiddenException {
        if (study.isLocked()) {
            String errorMsg = MessagesStrings.studyLocked(study.getId());
            throw new ForbiddenException(errorMsg);
        }
    }

    /**
     * Checks the study and throws an Exception in case of a problem.
     */
    public void checkStandardForStudy(Study study, Long studyId, User user)
            throws ForbiddenException, NotFoundException {
        if (study == null) {
            String errorMsg = MessagesStrings.studyNotExist(studyId);
            throw new NotFoundException(errorMsg);
        }
        // Check that the user is a member of the study or a superuser
        if (!(study.hasUser(user) || Helpers.isAllowedSuperuser(user))) {
            String errorMsg = "No access to study with ID " + studyId;
            throw new ForbiddenException(errorMsg);
        }
    }

    /**
     * Checks a list of ComponentResult. Checks each ComponentResult whether the belonging Study and Component are fine
     * (checkStandard). It also checks whether the study is locked.
     *
     * @param componentResultList  A list of ComponentResults
     * @param user                 The study that corresponds to the results must have this user otherwise
     *                             ForbiddenException will be thrown.
     * @param studyMustNotBeLocked If true the study that corresponds to the results must not be locked and it will
     *                             throw an ForbiddenException.
     */
    public void checkComponentResults(List<ComponentResult> componentResultList, User user,
            boolean studyMustNotBeLocked) throws ForbiddenException, NotFoundException {
        for (ComponentResult componentResult : componentResultList) {
            checkComponentResult(componentResult, user, studyMustNotBeLocked);
        }
    }

    /**
     * Checks a ComponentResult whether the belonging Study and Component are fine (checkStandard). It also checks
     * whether the study is locked.
     *
     * @param componentResult      A ComponentResults
     * @param user                 The study that corresponds to the results must have this user otherwise
     *                             ForbiddenException will be thrown.
     * @param studyMustNotBeLocked If true the study that corresponds to the results must not be locked and it will
     *                             throw an ForbiddenException.
     */
    public <T extends ComponentResult> void checkComponentResult(T componentResult, User user, boolean studyMustNotBeLocked)
            throws ForbiddenException, NotFoundException {
        Component component = componentResult.getComponent();
        Study study = component.getStudy();
        checkStandardForComponent(component.getId(), component, user);
        if (studyMustNotBeLocked) {
            checkStudyLocked(study);
        }
    }

    /**
     * Checks a StudyResult whether the belonging Study is fine, especially that the StudyResult belongs to this user.
     * It also checks whether the study is locked.
     *
     * @param studyResult          A StudyResults
     * @param user                 The study that corresponds to the results must have this user otherwise
     *                             ForbiddenException will be thrown.
     * @param studyMustNotBeLocked If true the study that corresponds to the results must not be locked and it will
     *                             throw an ForbiddenException.
     */
    public void checkStudyResult(StudyResult studyResult, User user, boolean studyMustNotBeLocked)
            throws ForbiddenException, NotFoundException {
        Study study = studyResult.getStudy();
        checkStandardForStudy(study, study.getId(), user);
        if (studyMustNotBeLocked) {
            checkStudyLocked(study);
        }
    }

    /**
     * Checks a list of StudyResult. Checks each StudyResult whether the belonging Study is fine, especially that the
     * StudyResult belongs to this user. It also checks whether the study is locked.
     *
     * @param studyResultList      A list of StudyResults
     * @param user                 The study that corresponds to the results must have this user otherwise
     *                             ForbiddenException will be thrown.
     * @param studyMustNotBeLocked If true the study that corresponds to the results must not be locked and it will
     *                             throw an ForbiddenException.
     */
    public void checkStudyResults(List<StudyResult> studyResultList, User user, boolean studyMustNotBeLocked)
            throws ForbiddenException, NotFoundException {
        for (StudyResult studyResult : studyResultList) {
            checkStudyResult(studyResult, user, studyMustNotBeLocked);
        }
    }

    /**
     * Throws a Exception in case the worker doesn't exist. Distinguishes between normal and Ajax request.
     */
    public void checkWorker(Worker worker, Long workerId) throws BadRequestException {
        if (worker == null) {
            throw new BadRequestException(MessagesStrings.workerNotExist(workerId));
        }
    }

    public void isUserAllowedToAccessWorker(User user, Worker worker) throws ForbiddenException {
        List<Worker> workerList = user.getStudyList().stream()
                .map(Study::getBatchList)
                .flatMap(List::stream)
                .map(Batch::getWorkerList)
                .flatMap(Set::stream)
                .collect(Collectors.toList());
        if (!workerList.contains(worker)) {
            throw new ForbiddenException("User is not allowed to access this Worker");
        }
    }

}
