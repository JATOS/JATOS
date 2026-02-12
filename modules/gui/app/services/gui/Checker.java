package services.gui;

import exceptions.gui.ForbiddenException;
import exceptions.gui.NotFoundException;
import models.common.*;
import models.common.workers.Worker;
import models.gui.ApiEnvelope.ErrorCode;
import utils.common.Helpers;

import javax.inject.Singleton;
import java.util.List;
import java.util.Set;


/**
 * Service class that provides authorization and non-null checks for different entities
 *
 * @author Kristian Lange
 */
@Singleton
public class Checker {

    public void canUserAccessComponent(Component component, User user)
            throws NotFoundException, ForbiddenException {
        canUserAccessComponent(component, user, false);
    }

    public void canUserAccessComponent(Component component, User user, boolean studyMustNotBeLocked)
            throws NotFoundException, ForbiddenException {
        if (component == null) {
            throw new NotFoundException("Component doesn't exist.");
        }
        Study study = component.getStudy();
        canUserAccessStudy(study, user);
        checkStudyNotLocked(study, studyMustNotBeLocked);
    }

    public void canUserAccessBatch(Batch batch, User user)
            throws NotFoundException, ForbiddenException {
        canUserAccessBatch(batch, user, false);
    }

    public void canUserAccessBatch(Batch batch, User user, boolean studyMustNotBeLocked)
            throws NotFoundException, ForbiddenException {
        if (batch == null) {
            throw new NotFoundException("Batch doesn't exist.");
        }
        Study study = batch.getStudy();
        canUserAccessStudy(study, user);
        checkStudyNotLocked(study, studyMustNotBeLocked);
    }

    public void canUserAccessStudyLink(StudyLink studyLink, User user) throws ForbiddenException, NotFoundException {
        canUserAccessStudyLink(studyLink, user, false);
    }

    public void canUserAccessStudyLink(StudyLink studyLink, User user, boolean studyMustNotBeLocked)
            throws NotFoundException, ForbiddenException {
        if (studyLink == null) {
            throw new NotFoundException("Study code doesn't exist.");
        }
        Study study = studyLink.getBatch().getStudy();
        canUserAccessStudy(study, user);
        checkStudyNotLocked(study, studyMustNotBeLocked);
    }

    public void canUserAccessGroupResult(GroupResult groupResult, User user)
            throws ForbiddenException, NotFoundException {
        canUserAccessGroupResult(groupResult, user, false);
    }

    public void canUserAccessGroupResult(GroupResult groupResult, User user, boolean studyMustNotBeLocked)
            throws ForbiddenException, NotFoundException {
        if (groupResult == null) {
            throw new NotFoundException("GroupResult does not exist");
        }
        Study study = groupResult.getBatch().getStudy();
        canUserAccessStudy(study, user);
        checkStudyNotLocked(study, studyMustNotBeLocked);
    }

    public void checkNotDefaultBatch(Batch batch) throws ForbiddenException {
        if (batch.equals(batch.getStudy().getDefaultBatch())) {
            throw new ForbiddenException("Not default batch.");
        }
    }

    public void checkStudyNotLocked(Study study) throws ForbiddenException {
        checkStudyNotLocked(study, true);
    }

    private void checkStudyNotLocked(Study study, boolean studyMustNotBeLocked) throws ForbiddenException {
        if (studyMustNotBeLocked && study.isLocked()) {
            throw new ForbiddenException("Study locked", ErrorCode.STUDY_LOCKED);
        }
    }

    public void canUserAccessStudy(Study study, User user) throws ForbiddenException, NotFoundException {
        canUserAccessStudy(study, user, false);
    }

    public void canUserAccessStudy(Study study, User user, boolean studyMustNotBeLocked)
            throws ForbiddenException, NotFoundException {
        if (study == null) {
            throw new NotFoundException("Study doesn't exist.");
        }
        // Check that the user is a member of the study or a superuser
        if (!(study.hasUser(user) || Helpers.isAllowedSuperuser(user))) {
            throw new ForbiddenException("No access to study.", ErrorCode.NO_ACCESS);
        }
        checkStudyNotLocked(study, studyMustNotBeLocked);
    }

    public void canUserAccessComponentResults(List<ComponentResult> componentResultList, User user,
                                              boolean studyMustNotBeLocked)
            throws ForbiddenException, NotFoundException {
        for (ComponentResult componentResult : componentResultList) {
            canUserAccessComponentResult(componentResult, user, studyMustNotBeLocked);
        }
    }

    public void canUserAccessComponentResult(ComponentResult componentResult, User user, boolean studyMustNotBeLocked)
            throws ForbiddenException, NotFoundException {
        if (componentResult == null) {
            throw new NotFoundException("Component doesn't exists");
        }
        Study study = componentResult.getComponent().getStudy();
        canUserAccessStudy(study, user);
        checkStudyNotLocked(study, studyMustNotBeLocked);
    }

    public void canUserAccessStudyResults(List<StudyResult> studyResultList, User user, boolean studyMustNotBeLocked)
            throws ForbiddenException, NotFoundException {
        for (StudyResult studyResult : studyResultList) {
            canUserAccessStudyResult(studyResult, user, studyMustNotBeLocked);
        }
    }

    public void canUserAccessStudyResult(StudyResult studyResult, User user, boolean studyMustNotBeLocked)
            throws ForbiddenException, NotFoundException {
        if (studyResult == null) {
            throw new NotFoundException("StudyResult doesn't exists");
        }
        Study study = studyResult.getStudy();
        canUserAccessStudy(study, user);
        checkStudyNotLocked(study, studyMustNotBeLocked);
    }

    public void canUserAccessWorker(User user, Worker worker) throws ForbiddenException, NotFoundException {
        if (worker == null) {
            throw new NotFoundException("Worker doesn't exist");
        }
        boolean allowed = user.getStudyList().stream()
                .map(Study::getBatchList)
                .flatMap(List::stream)
                .map(Batch::getWorkerList)
                .flatMap(Set::stream)
                .anyMatch(w -> w.equals(worker));
        if (!allowed) {
            throw new ForbiddenException("User is not allowed to access this Worker", ErrorCode.NO_ACCESS);
        }
    }

    public void checkAdminOrSelf(User signedinUser, User user) throws ForbiddenException {
        if (!signedinUser.isAdmin() && !signedinUser.equals(user)) {
            throw new ForbiddenException("You are not allowed to access this user", ErrorCode.NO_ACCESS);
        }
    }


}
