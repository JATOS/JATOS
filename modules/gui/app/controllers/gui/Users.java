package controllers.gui;

import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import daos.common.UserDao;
import exceptions.gui.ForbiddenException;
import exceptions.gui.JatosGuiException;
import exceptions.gui.NotFoundException;
import general.common.MessagesStrings;
import models.common.User;
import models.common.User.AuthMethod;
import models.common.User.Role;
import models.gui.ChangePasswordModel;
import models.gui.ChangeUserProfileModel;
import models.gui.NewUserModel;
import play.Logger;
import play.data.DynamicForm;
import play.data.Form;
import play.data.FormFactory;
import play.data.validation.ValidationError;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import services.gui.*;
import utils.common.HttpUtils;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.naming.NamingException;
import java.io.IOException;
import java.util.List;

/**
 * Controller with actions concerning users
 *
 * @author Kristian Lange
 */
@GuiAccessLogging
@Singleton
public class Users extends Controller {

    private static final Logger.ALogger LOGGER = Logger.of(Users.class);

    private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
    private final UserDao userDao;
    private final UserService userService;
    private final AuthenticationService authenticationService;
    private final AuthenticationValidation authenticationValidation;
    private final BreadcrumbsService breadcrumbsService;
    private final FormFactory formFactory;
    private final JsonUtils jsonUtils;

    @Inject
    Users(JatosGuiExceptionThrower jatosGuiExceptionThrower,
            UserDao userDao, UserService userService,
            AuthenticationService authenticationService,
            AuthenticationValidation authenticationValidation,
            BreadcrumbsService breadcrumbsService, FormFactory formFactory,
            JsonUtils jsonUtils) {
        this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
        this.userDao = userDao;
        this.userService = userService;
        this.authenticationService = authenticationService;
        this.authenticationValidation = authenticationValidation;
        this.breadcrumbsService = breadcrumbsService;
        this.formFactory = formFactory;
        this.jsonUtils = jsonUtils;
    }

    @Transactional
    @Authenticated(Role.ADMIN)
    public Result userManager() {
        User loggedInUser = authenticationService.getLoggedInUser();
        String breadcrumbs = breadcrumbsService.generateForHome(BreadcrumbsService.USER_MANAGER);
        return ok(views.html.gui.user.userManager.render(loggedInUser,
                breadcrumbs, HttpUtils.isLocalhost()));
    }

    /**
     * Ajax GET request: Returns a list of all users as JSON
     */
    @Transactional
    @Authenticated(Role.ADMIN)
    public Result allUserData() {
        List<User> userList = userService.retrieveAllUsers();
        return ok(jsonUtils.userData(userList));
    }

    /**
     * Ajax POST
     * <p>
     * Request to add or remove the ADMIN role from a user.
     */
    @Transactional
    @Authenticated(Role.ADMIN)
    public Result toggleAdmin(String usernameOfUserToChange, Boolean adminRole) {
        boolean hasAdminRole;
        try {
            String normalizedUsernameOfUserToChange = User.normalizeUsername(usernameOfUserToChange);
            hasAdminRole = userService.changeAdminRole(normalizedUsernameOfUserToChange, adminRole);
        } catch (NotFoundException e) {
            return badRequest(e.getMessage());
        } catch (ForbiddenException e) {
            return forbidden(e.getMessage());
        }
        return ok(jsonUtils.asJsonNode(hasAdminRole));
    }

    /**
     * Shows the profile view of a user
     */
    @Transactional
    @Authenticated
    public Result profile(String username) throws JatosGuiException {
        String normalizedUsername = User.normalizeUsername(username);
        User loggedInUser = authenticationService.getLoggedInUser();
        checkUsernameIsOfLoggedInUser(normalizedUsername, loggedInUser);

        String breadcrumbs = breadcrumbsService.generateForUser(loggedInUser);
        return ok(views.html.gui.user.profile.render(loggedInUser, breadcrumbs, HttpUtils.isLocalhost()));
    }

    /**
     * Ajax GET request: Returns data of the user that belongs to the given username
     */
    @Transactional
    @Authenticated
    public Result singleUserData(String username) throws JatosGuiException {
        User loggedInUser = authenticationService.getLoggedInUser();
        String normalizedUsername = User.normalizeUsername(username);
        checkUsernameIsOfLoggedInUser(normalizedUsername, loggedInUser);
        return ok(jsonUtils.userData(loggedInUser));
    }

    /**
     * Handles POST request of user create form. Only users with Role ADMIN are
     * allowed to create new users.
     */
    @Transactional
    @Authenticated(Role.ADMIN)
    public Result submitCreated() {
        User loggedInUser = authenticationService.getLoggedInUser();
        Form<NewUserModel> form = formFactory.form(NewUserModel.class).bindFromRequest();

        // Validate
        form = authenticationValidation.validateNewUser(form);
        if (form.hasErrors()) return badRequest(form.errorsAsJson());

        // Check if user with this username already exists
        String normalizedUsername = User.normalizeUsername(form.get().getUsername());
        User existingUser = userDao.findByUsername(normalizedUsername);
        if (existingUser != null) {
            form = form.withError(
                    new ValidationError(NewUserModel.USERNAME, MessagesStrings.THIS_USERNAME_IS_ALREADY_REGISTERED));
            return forbidden(form.errorsAsJson());
        }

        // Authenticate: check admin password
        try {
            String adminPassword = form.get().getAdminPassword();
            if (!authenticationService.authenticate(loggedInUser.getUsername(), adminPassword)) {
                form = form.withError(new ValidationError(NewUserModel.ADMIN_PASSWORD, MessagesStrings.WRONG_PASSWORD));
                return forbidden(form.errorsAsJson());
            }
        } catch (NamingException e) {
            LOGGER.warn("LDAP problems - " + e.toString());
            return internalServerError(MessagesStrings.LDAP_PROBLEMS);
        }

        userService.bindToUserAndPersist(form.get());
        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

    /**
     * Handles POST request of user edit profile form (so far it's only the
     * user's name - password is handled in another method).
     */
    @Transactional
    @Authenticated
    public Result submitEditedProfile(String username) throws JatosGuiException {
        User loggedInUser = authenticationService.getLoggedInUser();
        String normalizedUsername = User.normalizeUsername(username);

        if (loggedInUser.getAuthMethod() == AuthMethod.OAUTH_GOOGLE) {
            return forbidden("Users signed in by Google can't change their name.");
        }

        checkUsernameIsOfLoggedInUser(normalizedUsername, loggedInUser);

        Form<ChangeUserProfileModel> form = formFactory.form(ChangeUserProfileModel.class).bindFromRequest();
        if (form.hasErrors()) return badRequest(form.errorsAsJson());

        // Update user in database: so far it's only the user's name
        String name = form.get().getName();
        userService.updateName(loggedInUser, name);
        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

    /**
     * Handles POST request of change password form. Can be either origin in the
     * user manager or in the user profile.
     */
    @Transactional
    @Authenticated
    public Result submitChangedPassword(String usernameOfUserToChange) throws NamingException {
        User loggedInUser = authenticationService.getLoggedInUser();
        Form<ChangePasswordModel> form = formFactory.form(ChangePasswordModel.class).bindFromRequest();
        String normalizedUsernameOfUserToChange = User.normalizeUsername(usernameOfUserToChange);

        User user;
        try {
            user = userService.retrieveUser(normalizedUsernameOfUserToChange);
        } catch (NotFoundException e) {
            return badRequest(e.getMessage());
        }

        if (user.getAuthMethod() == AuthMethod.LDAP || user.getAuthMethod() == AuthMethod.OAUTH_GOOGLE) {
            return forbidden("It's only possible to change the passwords of locally stored users"
                    + " - not LDAP users or Google sign-in users.");
        }

        // Only user 'admin' is allowed to change his password
        if (normalizedUsernameOfUserToChange.equals(UserService.ADMIN_USERNAME) &&
                !loggedInUser.getUsername().equals(UserService.ADMIN_USERNAME)) {
            form = form.withError(new ValidationError(ChangePasswordModel.ADMIN_PASSWORD,
                    MessagesStrings.NOT_ALLOWED_CHANGE_PW_ADMIN));
            return forbidden(form.errorsAsJson());
        }

        // Validate
        form = authenticationValidation.validateChangePassword(form);
        if (form.hasErrors()) return forbidden(form.errorsAsJson());

        // Authenticate: Admin changes a password for some other user
        if (loggedInUser.hasRole(Role.ADMIN) && form.get().getAdminPassword() != null) {
            String adminUsername = loggedInUser.getUsername();
            String adminPassword = form.get().getAdminPassword();
            if (!authenticationService.authenticate(adminUsername, adminPassword)) {
                form = form.withError(
                        new ValidationError(ChangePasswordModel.ADMIN_PASSWORD, MessagesStrings.WRONG_PASSWORD));
                return forbidden(form.errorsAsJson());
            }
            // Change password
            String newPassword = form.get().getNewPassword();
            userService.updatePassword(user, newPassword);
        }

        // Authenticate: An user changes their own password
        if (loggedInUser.getUsername().equals(normalizedUsernameOfUserToChange)
                && form.get().getOldPassword() != null) {
            String oldPassword = form.get().getOldPassword();
            if (!authenticationService.authenticate(normalizedUsernameOfUserToChange, oldPassword)) {
                form = form.withError(
                        new ValidationError(ChangePasswordModel.OLD_PASSWORD, MessagesStrings.WRONG_OLD_PASSWORD));
                return forbidden(form.errorsAsJson());
            }
            // Change password
            String newPassword = form.get().getNewPassword();
            userService.updatePassword(user, newPassword);
        }

        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

    /**
     * POST request to delete a user. Is called from user manager and user
     * profile.
     * <p>
     * It can't be a HTTP DELETE because it contains form data and Play doesn't
     * handle body data in a DELETE request.
     */
    @Transactional
    @Authenticated
    public Result remove(String usernameOfUserToRemove) {
        User loggedInUser = authenticationService.getLoggedInUser();
        String normalizedLoggedInUsername = loggedInUser.getUsername();
        String normalizedUsernameOfUserToRemove = User.normalizeUsername(usernameOfUserToRemove);
        if (!loggedInUser.hasRole(Role.ADMIN) && !normalizedUsernameOfUserToRemove.equals(normalizedLoggedInUsername)) {
            return forbidden(MessagesStrings.NOT_ALLOWED_TO_DELETE_USER);
        }

        DynamicForm requestData = formFactory.form().bindFromRequest();
        String password = requestData.get("password");
        try {
            if (password == null || !authenticationService.authenticate(normalizedLoggedInUsername, password)) {
                return forbidden(MessagesStrings.WRONG_PASSWORD);
            }
        } catch (NamingException e) {
            LOGGER.warn("LDAP problems - " + e.toString());
            return internalServerError(MessagesStrings.LDAP_PROBLEMS);
        }

        try {
            userService.removeUser(normalizedUsernameOfUserToRemove);
        } catch (NotFoundException e) {
            return badRequest(e.getMessage());
        } catch (ForbiddenException e) {
            return forbidden(e.getMessage());
        } catch (IOException e) {
            return internalServerError(e.getMessage());
        }
        // If the user removes himself: logout
        if (normalizedUsernameOfUserToRemove.equals(normalizedLoggedInUsername)) {
            authenticationService.clearSessionCookieAndSessionCache(session(),
                    loggedInUser.getUsername(), request().host());
        }
        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

    private void checkUsernameIsOfLoggedInUser(String normalizedUsername, User loggedInUser) throws JatosGuiException {
        if (!normalizedUsername.equals(loggedInUser.getUsername())) {
            ForbiddenException e = new ForbiddenException(MessagesStrings.userNotAllowedToGetData(normalizedUsername));
            jatosGuiExceptionThrower.throwRedirect(e, controllers.gui.routes.Home.home());
        }
    }

}
