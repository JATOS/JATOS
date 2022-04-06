package controllers.gui;

import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import daos.common.UserDao;
import exceptions.gui.ForbiddenException;
import exceptions.gui.JatosGuiException;
import exceptions.gui.NotFoundException;
import general.common.MessagesStrings;
import models.common.Study;
import models.common.User;
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
import play.mvc.Http;
import play.mvc.Result;
import services.gui.*;
import utils.common.Helpers;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.naming.NamingException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller with actions concerning users (incl. user manager)
 *
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
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
        String breadcrumbs = breadcrumbsService.generateForAdministration(BreadcrumbsService.USER_MANAGER);
        return ok(views.html.gui.admin.userManager.render(loggedInUser, breadcrumbs, Helpers.isLocalhost()));
    }

    /**
     * GET request: Returns a list of all users as JSON
     */
    @Transactional
    @Authenticated(Role.ADMIN)
    public Result allUserData() {
        List<Map<String, Object>> allUserData = new ArrayList<>();
        for (User user : userDao.findAll()) {
            Map<String, Object> userData = getSingleUserData(user);
            allUserData.add(userData);
        }
        return ok(jsonUtils.asJsonNode(allUserData));
    }

    private Map<String, Object> getSingleUserData(User user) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("active", user.isActive());
        userData.put("name", user.getName());
        userData.put("username", user.getUsername());
        userData.put("email", user.getEmail());
        userData.put("roleList", user.getRoleList());
        userData.put("authMethod", user.getAuthMethod().name());
        userData.put("studyCount", user.getStudyList().size());
        userData.put("lastLogin", Helpers.formatDate(user.getLastLogin()));
        List<Map<String, Object>> allStudiesData = new ArrayList<>();
        for (Study study : user.getStudyList()) {
            Map<String, Object> studyData = new HashMap<>();
            studyData.put("id", study.getId());
            studyData.put("title", study.getTitle());
            studyData.put("userSize", study.getUserList().size());
            allStudiesData.add(studyData);
        }
        userData.put("studyList", allStudiesData);
        return userData;
    }

    /**
     * POST request to activate or deactivate a user.
     */
    @Transactional
    @Authenticated(Role.ADMIN)
    public Result toggleActive(String usernameOfUserToChange, Boolean active) {
        try {
            String normalizedUsernameOfUserToChange = User.normalizeUsername(usernameOfUserToChange);
            userService.toggleActive(normalizedUsernameOfUserToChange, active);
        } catch (NotFoundException e) {
            return badRequest(e.getMessage());
        } catch (ForbiddenException e) {
            return forbidden(e.getMessage());
        }
        return ok();
    }

    /**
     * POST request to add or remove a role from a user.
     */
    @Transactional
    @Authenticated(Role.ADMIN)
    public Result toggleRole(String usernameOfUserToChange, String role, boolean value) {
        String normalizedUsernameOfUserToChange = User.normalizeUsername(usernameOfUserToChange);
        try {
            switch (Role.valueOf(role)) {
                case SUPERUSER:
                    return ok(jsonUtils.asJsonNode(
                            userService.changeSuperuserRole(normalizedUsernameOfUserToChange, value)));
                case ADMIN:
                    return ok(
                            jsonUtils.asJsonNode(userService.changeAdminRole(normalizedUsernameOfUserToChange, value)));
                default:
                    return badRequest("Unknown role");
            }
        } catch (NotFoundException e) {
            return badRequest(e.getMessage());
        } catch (ForbiddenException e) {
            return forbidden(e.getMessage());
        }
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
        return ok(views.html.gui.admin.profile.render(loggedInUser, breadcrumbs, Helpers.isLocalhost()));
    }

    /**
     * GET request that returns data of the user that belongs to the given username
     */
    @Transactional
    @Authenticated
    public Result singleUserData(String username) throws JatosGuiException {
        User loggedInUser = authenticationService.getLoggedInUser();
        String normalizedUsername = User.normalizeUsername(username);
        checkUsernameIsOfLoggedInUser(normalizedUsername, loggedInUser);
        return ok(jsonUtils.asJsonNode(getSingleUserData(loggedInUser)));
    }

    /**
     * Handles POST request of user create form. Only users with Role ADMIN are
     * allowed to create new users.
     */
    @Transactional
    @Authenticated(Role.ADMIN)
    public Result create(Http.Request request) throws NamingException {
        Form<NewUserModel> form = formFactory.form(NewUserModel.class).bindFromRequest(request);

        // Validate
        form = authenticationValidation.validateNewUser(form);
        if (form.hasErrors()) return badRequest(form.errorsAsJson());

        // Check admin password (except if Oauth Google)
        User loggedInUser = authenticationService.getLoggedInUser();
        String adminPassword = form.get().getAdminPassword();
        if (!loggedInUser.isOauthGoogle()
                && !authenticationService.authenticate(loggedInUser, adminPassword)) {
            form = form.withError(new ValidationError(NewUserModel.ADMIN_PASSWORD, "Wrong password"));
            return forbidden(form.errorsAsJson());
        }

        userService.bindToUserAndPersist(form.get());
        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

    /**
     * Handles POST request of user edit profile form (except password and roles).
     * This POST can come from the user themselves or from an admin user to edit another user.
     */
    @Transactional
    @Authenticated
    public Result edit(String username) throws JatosGuiException {
        User loggedInUser = authenticationService.getLoggedInUser();
        String normalizedUsernameOfUserToChange = User.normalizeUsername(username);
        User user = userDao.findByUsername(normalizedUsernameOfUserToChange);

        if (user.isOauthGoogle()) {
            return forbidden("Google authenticated users can't have their profile changed.");
        }

        if (!loggedInUser.isAdmin()) {
            checkUsernameIsOfLoggedInUser(normalizedUsernameOfUserToChange, loggedInUser);
        }

        Form<ChangeUserProfileModel> form = formFactory.form(ChangeUserProfileModel.class).bindFromRequest();
        if (form.hasErrors()) return badRequest(form.errorsAsJson());

        // Update user in database: so far it's only the user's name
        user.setName(form.get().getName());
        user.setEmail(form.get().getEmail());
        userDao.update(user);
        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

    /**
     * Handles POST request from change password form in user manager initiated by an admin
     */
    @Transactional
    @Authenticated
    public Result changePasswordByAdmin(Http.Request request) throws NamingException {
        User loggedInUser = authenticationService.getLoggedInUser();
        Form<ChangePasswordModel> form = formFactory.form(ChangePasswordModel.class).bindFromRequest(request);
        String normalizedUsernameOfUserToChange = form.get().getUsername();

        if (!loggedInUser.isAdmin()) {
            return forbidden("Only admin users are allowed to change passwords of other users.");
        }

        User user = userDao.findByUsername(normalizedUsernameOfUserToChange);
        if (user == null) {
            return badRequest("An user with username " + normalizedUsernameOfUserToChange + " doesn't exist.");
        }
        if (user.isLdap() || user.isOauthGoogle()) {
            return forbidden("It's not possible to change the password of LDAP or Google sign-in users.");
        }

        // Only user 'admin' is allowed to change his own password
        if (normalizedUsernameOfUserToChange.equals(UserService.ADMIN_USERNAME) &&
                !loggedInUser.getUsername().equals(UserService.ADMIN_USERNAME)) {
            form = form.withError(new ValidationError(ChangePasswordModel.ADMIN_PASSWORD,
                    "It's not possible to change admin's password."));
            return forbidden(form.errorsAsJson());
        }

        // Validate
        form = authenticationValidation.validateChangePassword(form);
        if (form.hasErrors()) return forbidden(form.errorsAsJson());

        // Authenticate loggedInUser
        if (!loggedInUser.isOauthGoogle()) {
            String adminPassword = form.get().getAdminPassword();
            if (!authenticationService.authenticate(loggedInUser, adminPassword)) {
                form = form.withError(new ValidationError(ChangePasswordModel.ADMIN_PASSWORD, "Wrong password"));
                return forbidden(form.errorsAsJson());
            }
        }
        // Change password
        String newPassword = form.get().getNewPassword();
        userService.updatePassword(user, newPassword);

        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

    /**
     * Handles POST request from change password form by user themselves
     */
    @Transactional
    @Authenticated
    public Result changePasswordByUser(Http.Request request) throws NamingException {
        User loggedInUser = authenticationService.getLoggedInUser();
        Form<ChangePasswordModel> form = formFactory.form(ChangePasswordModel.class).bindFromRequest(request);
        String normalizedUsernameOfUserToChange = form.get().getUsername();

        if (!loggedInUser.getUsername().equals(normalizedUsernameOfUserToChange)) {
            return forbidden("User can change only their own password");
        }

        if (loggedInUser.isLdap() || loggedInUser.isOauthGoogle()) {
            return forbidden("It's not possible to change the password of LDAP or Google sign-in users.");
        }

        // Validate
        form = authenticationValidation.validateChangePassword(form);
        if (form.hasErrors()) return forbidden(form.errorsAsJson());

        // Check old password
        String oldPassword = form.get().getOldPassword();
        if (!authenticationService.authenticate(loggedInUser, oldPassword)) {
            form = form.withError(new ValidationError(ChangePasswordModel.OLD_PASSWORD, "Wrong password"));
            return forbidden(form.errorsAsJson());
        }

        // Change password
        String newPassword = form.get().getNewPassword();
        userService.updatePassword(loggedInUser, newPassword);

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
        if (!loggedInUser.isAdmin() && !normalizedUsernameOfUserToRemove.equals(normalizedLoggedInUsername)) {
            return forbidden(MessagesStrings.NOT_ALLOWED_TO_DELETE_USER);
        }

        DynamicForm requestData = formFactory.form().bindFromRequest();
        if (!loggedInUser.isOauthGoogle()) {
            try {
                String password = requestData.get("password");
                if (!authenticationService.authenticate(loggedInUser, password)) {
                    return forbidden(MessagesStrings.WRONG_PASSWORD);
                }
            } catch (NamingException e) {
                LOGGER.warn("LDAP problems - " + e);
                return internalServerError(MessagesStrings.LDAP_PROBLEMS);
            }
        } else {
            // Google Oauth users confirm with their email address (stored in username)
            String username = requestData.get("username");
            if (!username.equals(loggedInUser.getUsername())) {
                return forbidden(MessagesStrings.WRONG_USERNAME);
            }
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
