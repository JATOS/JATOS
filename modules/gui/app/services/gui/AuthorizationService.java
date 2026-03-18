package services.gui;

import exceptions.gui.ForbiddenException;
import exceptions.gui.NotFoundException;
import models.common.*;
import models.common.workers.Worker;
import general.common.ApiEnvelope.ErrorCode;
import models.gui.NewUserProperties;
import models.gui.UserProperties;
import utils.common.Helpers;

import javax.inject.Singleton;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static models.common.User.AuthMethod.DB;
import static models.common.User.AuthMethod.LDAP;
import static services.gui.UserService.ADMIN_USERNAME;


/**
 * Service class that provides authorization and non-null checks for different objects
 *
 * @author Kristian Lange
 */
@Singleton
public class AuthorizationService {

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

    public void checkAdminOrSelf(User signedinUser, User user) throws ForbiddenException, NotFoundException {
        if (user == null) {
            throw new NotFoundException("User not found");
        }
        if (!signedinUser.isAdmin() && !signedinUser.equals(user)) {
            throw new ForbiddenException("You are not allowed to access this user", ErrorCode.NO_ACCESS);
        }
    }

    public void checkAuthMethodIsDbOrLdap(NewUserProperties props) throws ForbiddenException {
        if (!Arrays.asList(DB, LDAP).contains(props.getAuthMethod())) {
            throw new ForbiddenException("Invalid authentication method", ErrorCode.INVALID_AUTH_METHOD);
        }
    }

    public void checkAuthMethodIsDbOrLdap(User user) throws ForbiddenException, NotFoundException {
        if (user == null) {
            throw new NotFoundException("User not found");
        }
        if (!Arrays.asList(DB, LDAP).contains(user.getAuthMethod())) {
            throw new ForbiddenException("Invalid authentication method", ErrorCode.INVALID_AUTH_METHOD);
        }
    }

    public void checkNotUserAdmin(User user) throws ForbiddenException, NotFoundException {
        if (user == null) {
            throw new NotFoundException("User not found");
        }
        if (user.getUsername().equals(ADMIN_USERNAME)) {
            throw new ForbiddenException("The ADMIN role cannot be removed from user ‘admin’, and the user cannot be deleted.");
        }
    }

    public void checkNotYourself(User signedinUser, User user) throws ForbiddenException, NotFoundException {
        if (user == null) {
            throw new NotFoundException("User not found");
        }
        if (user.getId().equals(signedinUser.getId())) {
            throw new ForbiddenException("You cannot change this property of your own user.");
        }
    }

    public void checkSignedinUserAllowedToChangeUser(UserProperties props, User signedinUser, User user) throws ForbiddenException {
        final boolean isPropsAdminUser = ADMIN_USERNAME.equals(props.getUsername());
        final boolean isSignedinAdminUser = ADMIN_USERNAME.equals(signedinUser.getUsername());
        final boolean passwordChangeRequested = props.getPassword() != null;

        // Only user 'admin' is allowed to change their password
        if (passwordChangeRequested && isPropsAdminUser && !isSignedinAdminUser) {
            throw new ForbiddenException("Only user 'admin' can change their own password");
        }
        // User 'admin' cannot be deactivated
        if (!props.isActive() && isPropsAdminUser) {
            throw new ForbiddenException("User 'admin' cannot be deactivated");
        }
        // A user cannot deactivate themselves
        if (!props.isActive() && signedinUser.equals(user)) {
            throw new ForbiddenException("A user cannot deactivate themselves");
        }
        // LDAP users cannot change their password
        if (passwordChangeRequested && user.isLdap()) {
            throw new ForbiddenException("LDAP user's password cannot be changed");
        }
    }

    public void checkSignedinUserAllowedToAccessUser(User user, User signedinUser) throws ForbiddenException, NotFoundException {
        if (user == null) {
            throw new NotFoundException("User not found");
        }
        boolean isUser = signedinUser.equals(user);
        boolean isAdminGeneratingNonAdminToken = signedinUser.isAdmin() && !user.isAdmin();
        if (!(isUser || isAdminGeneratingNonAdminToken)) {
            throw new ForbiddenException("Not allowed to access this user");
        }
    }

    public void checkUserAllowedToAccessApiToken(ApiToken token, User signedinUser) throws ForbiddenException, NotFoundException {
        if (token == null) {
            throw new NotFoundException("Token not found");
        }
        checkSignedinUserAllowedToAccessUser(token.getUser(), signedinUser);
    }

    public void checkUserExists(User user) throws NotFoundException {
        if (user == null) {
            throw new NotFoundException("User not found");
        }
    }

}
