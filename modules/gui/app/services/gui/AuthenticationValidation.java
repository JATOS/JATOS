package services.gui;

import daos.common.UserDao;
import general.common.MessagesStrings;
import models.common.User;
import models.common.User.Role;
import models.gui.ChangePasswordModel;
import models.gui.NewUserModel;
import play.data.validation.ValidationError;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

/**
 * Service class that validates models that create, change or delete users.
 * Usually this validation is part of the model class, but since this is
 * concerns authentication and it used other service and DAO classes I put it in
 * an extra class.
 *
 * @author Kristian Lange (2017)
 */
@Singleton
public class AuthenticationValidation {

    private final AuthenticationService authenticationService;
    private final UserDao userDao;

    @Inject
    AuthenticationValidation(AuthenticationService authenticationService,
            UserDao userDao) {
        this.authenticationService = authenticationService;
        this.userDao = userDao;
    }

    /**
     * Validates a NewUserModel and returns a list with errors. Usually this is
     * done in the model class, but since here the user DAO is needed I put it
     * in an extra class. In the NewUserModel are still some simple validations.
     */
    public List<ValidationError> validateNewUser(NewUserModel newUserModel,
            String loggedInAdminEmail) {
        List<ValidationError> errorList = new ArrayList<>();

        // Check if user with this email already exists
        String email = newUserModel.getEmail();
        User existingUser = userDao.findByEmail(email);
        if (existingUser != null) {
            errorList.add(new ValidationError(NewUserModel.EMAIL,
                    MessagesStrings.THIS_EMAIL_IS_ALREADY_REGISTERED));
        }

        // Check both passwords equal
        String password = newUserModel.getPassword();
        String passwordRepeat = newUserModel.getPasswordRepeat();
        if (!password.equals(passwordRepeat)) {
            errorList.add(new ValidationError(NewUserModel.PASSWORD,
                    MessagesStrings.PASSWORDS_DONT_MATCH));
        }

        // Authenticate: check admin password
        String adminPassword = newUserModel.getAdminPassword();
        if (!authenticationService.authenticate(loggedInAdminEmail, adminPassword)) {
            errorList.add(new ValidationError(NewUserModel.ADMIN_PASSWORD,
                    MessagesStrings.WRONG_PASSWORD));
        }

        return errorList;
    }

    /**
     * Validates a ChangePasswordModel and returns a list with errors. It can
     * either originate in the GUI in the user manager or in the user profile.
     * Both cases have to be validated differently. Usually this would be done
     * in the ChangePasswordModel class, but since here the user DAO is needed I
     * put it in an extra class.
     */
    public List<ValidationError> validateChangePassword(String emailOfUserToChange,
            ChangePasswordModel changePasswordModel) {
        emailOfUserToChange = emailOfUserToChange.toLowerCase();
        User loggedInUser = authenticationService.getLoggedInUser();

        // All form related errors go into errorList
        List<ValidationError> errorList = new ArrayList<>();

        // Only user 'admin' is allowed to change his password
        if (emailOfUserToChange.equals(UserService.ADMIN_EMAIL)
                && !loggedInUser.getEmail().equals(UserService.ADMIN_EMAIL)) {
            errorList.add(new ValidationError(ChangePasswordModel.ADMIN_PASSWORD,
                    MessagesStrings.NOT_ALLOWED_CHANGE_PW_ADMIN));
        }

        // Check both passwords equal
        String newPassword = changePasswordModel.getNewPassword();
        String newPasswordRepeat = changePasswordModel.getNewPasswordRepeat();
        if (!newPassword.equals(newPasswordRepeat)) {
            errorList.add(new ValidationError(ChangePasswordModel.NEW_PASSWORD,
                    MessagesStrings.PASSWORDS_DONT_MATCH));
        }

        // Authenticate: Either admin changes a password for some other user
        // or an user changes their own password
        if (loggedInUser.hasRole(Role.ADMIN)
                && changePasswordModel.getAdminPassword() != null) {
            String adminEmail = loggedInUser.getEmail();
            String adminPassword = changePasswordModel.getAdminPassword();
            if (!authenticationService.authenticate(adminEmail, adminPassword)) {
                errorList.add(new ValidationError(ChangePasswordModel.ADMIN_PASSWORD,
                        MessagesStrings.WRONG_PASSWORD));
            }

        } else if (loggedInUser.getEmail().equals(emailOfUserToChange)
                && changePasswordModel.getOldPassword() != null) {
            String oldPassword = changePasswordModel.getOldPassword();
            if (!authenticationService.authenticate(emailOfUserToChange, oldPassword)) {
                errorList.add(new ValidationError(ChangePasswordModel.OLD_PASSWORD,
                        MessagesStrings.WRONG_OLD_PASSWORD));
            }

        } else {
            // Should never happen since we checked role ADMIN already
            errorList.add(new ValidationError(ChangePasswordModel.ADMIN_PASSWORD,
                    MessagesStrings.NOT_ALLOWED_TO_CHANGE_PASSWORDS));
        }

        return errorList;
    }

}
