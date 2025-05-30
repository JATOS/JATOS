package controllers.gui;

import auth.gui.AuthAction.Auth;
import auth.gui.AuthService;
import auth.gui.SigninFormValidation;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import daos.common.UserDao;
import exceptions.gui.ForbiddenException;
import exceptions.gui.JatosGuiException;
import exceptions.gui.NotFoundException;
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
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.BreadcrumbsService;
import services.gui.JatosGuiExceptionThrower;
import services.gui.UserService;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.naming.NamingException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static controllers.gui.actionannotations.SaveLastVisitedPageUrlAction.SaveLastVisitedPageUrl;

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
    private final AuthService authService;
    private final SigninFormValidation signinFormValidation;
    private final BreadcrumbsService breadcrumbsService;
    private final FormFactory formFactory;
    private final JsonUtils jsonUtils;

    @Inject
    Users(JatosGuiExceptionThrower jatosGuiExceptionThrower,
            UserDao userDao, UserService userService,
            AuthService authService,
            SigninFormValidation signinFormValidation,
            BreadcrumbsService breadcrumbsService, FormFactory formFactory,
            JsonUtils jsonUtils) {
        this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
        this.userDao = userDao;
        this.userService = userService;
        this.authService = authService;
        this.signinFormValidation = signinFormValidation;
        this.breadcrumbsService = breadcrumbsService;
        this.formFactory = formFactory;
        this.jsonUtils = jsonUtils;
    }

    @Transactional
    @Auth(Role.ADMIN)
    @SaveLastVisitedPageUrl
    public Result userManager(Http.Request request) {
        User signedinUser = authService.getSignedinUser();
        String breadcrumbs = breadcrumbsService.generateForAdministration(BreadcrumbsService.USER_MANAGER);
        return ok(views.html.gui.admin.userManager.render(request, signedinUser, breadcrumbs));
    }

    /**
     * GET request: Returns a list of all users as JSON
     */
    @Transactional
    @Auth(Role.ADMIN)
    public Result allUserData() {
        List<Map<String, Object>> allUserData = new ArrayList<>();
        for (User user : userDao.findAll()) {
            Map<String, Object> userData = jsonUtils.getSingleUserData(user);
            allUserData.add(userData);
        }
        return ok(JsonUtils.asJsonNode(allUserData));
    }

    /**
     * POST request to activate or deactivate a user.
     */
    @Transactional
    @Auth(Role.ADMIN)
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
    @Auth(Role.ADMIN)
    public Result toggleRole(String usernameOfUserToChange, String role, boolean value) {
        String normalizedUsernameOfUserToChange = User.normalizeUsername(usernameOfUserToChange);
        try {
            switch (Role.valueOf(role)) {
                case SUPERUSER:
                    return ok(JsonUtils.asJsonNode(
                            userService.changeSuperuserRole(normalizedUsernameOfUserToChange, value)));
                case ADMIN:
                    return ok(JsonUtils.asJsonNode(
                            userService.changeAdminRole(normalizedUsernameOfUserToChange, value)));
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
     * GET request that returns user data of the signed-in user
     */
    @Transactional
    @Auth
    public Result signedinUserData() {
        User signedinUser = authService.getSignedinUser();
        return ok(JsonUtils.asJsonNode(jsonUtils.getSingleUserData(signedinUser)));
    }

    /**
     * Handles POST request of user create form. Only users with Role ADMIN are
     * allowed to create new users.
     */
    @Transactional
    @Auth(Role.ADMIN)
    public Result create(Http.Request request) throws NamingException {
        Form<NewUserModel> form = formFactory.form(NewUserModel.class).bindFromRequest(request);
        form = signinFormValidation.validateNewUser(form);
        if (form.hasErrors()) return badRequest(form.errorsAsJson());

        User newUser = userService.bindToUserAndPersist(form.get());
        return ok(JsonUtils.asJsonNode(jsonUtils.getSingleUserData(newUser)));
    }

    /**
     * Handles POST request of user edit profile form (except password and roles).
     * This POST can come from the user themselves or from an admin user to edit another user.
     */
    @Transactional
    @Auth
    public Result edit(String username) throws JatosGuiException {
        User signedinUser = authService.getSignedinUser();
        String normalizedUsernameOfUserToChange = User.normalizeUsername(username);
        User user = userDao.findByUsername(normalizedUsernameOfUserToChange);

        if (user.isOauthGoogle()) {
            return forbidden("Google authenticated users can't have their profile changed.");
        }

        if (user.isOidc()) {
            return forbidden("OIDC authenticated users can't have their profile changed.");
        }

        if (user.isOrcid()) {
            return forbidden("ORCID authenticated users can't have their profile changed.");
        }

        if (user.isSram()) {
            return forbidden("SRAM authenticated users can't have their profile changed.");
        }

        if (user.isConext()) {
            return forbidden("CONEXT authenticated users can't have their profile changed.");
        }

        if (!signedinUser.isAdmin()) {
            checkUsernameIsOfSignedinUser(normalizedUsernameOfUserToChange, signedinUser);
        }

        Form<ChangeUserProfileModel> form = formFactory.form(ChangeUserProfileModel.class).bindFromRequest();
        if (form.hasErrors()) return badRequest(form.errorsAsJson());

        // Update user in database: so far it's only the user's name
        user.setName(form.get().getName());
        user.setEmail(form.get().getEmail());
        userDao.update(user);
        return ok();
    }

    /**
     * Handles POST request from change password form in user manager initiated by an admin
     */
    @Transactional
    @Auth
    public Result changePasswordByAdmin(Http.Request request) throws NamingException {
        User signedinUser = authService.getSignedinUser();
        Form<ChangePasswordModel> form = formFactory.form(ChangePasswordModel.class).bindFromRequest(request);
        String normalizedUsernameOfUserToChange = form.get().getUsername();

        if (!signedinUser.isAdmin()) {
            return forbidden("Only admin users are allowed to change passwords of other users.");
        }

        User user = userDao.findByUsername(normalizedUsernameOfUserToChange);
        if (user == null) {
            return badRequest("An user with username \"" + normalizedUsernameOfUserToChange + "\" doesn't exist.");
        }
        if (!user.isDb()) {
            return forbidden("It's not possible to change the password of non-local authenticated users.");
        }

        // Only the user 'admin' is allowed to change his own password
        if (normalizedUsernameOfUserToChange.equals(UserService.ADMIN_USERNAME) &&
                !signedinUser.getUsername().equals(UserService.ADMIN_USERNAME)) {
            form = form.withError(new ValidationError(ChangePasswordModel.USERNAME,
                    "It's not possible to change admin's password."));
            return forbidden(form.errorsAsJson());
        }

        // Admins do not have to repeat the password
        form.get().setNewPasswordRepeat(form.get().getNewPassword());
        // Validate
        form = signinFormValidation.validateChangePassword(form);
        if (form.hasErrors()) return forbidden(form.errorsAsJson());

        // Change password
        String newPassword = form.get().getNewPassword();
        userService.updatePassword(user, newPassword);

        return ok();
    }

    /**
     * Handles POST request from change password form by user themselves
     */
    @Transactional
    @Auth
    public Result changePasswordByUser(Http.Request request) throws NamingException {
        User signedinUser = authService.getSignedinUser();
        Form<ChangePasswordModel> form = formFactory.form(ChangePasswordModel.class).bindFromRequest(request);
        String normalizedUsernameOfUserToChange = form.get().getUsername();

        if (!signedinUser.getUsername().equals(normalizedUsernameOfUserToChange)) {
            return forbidden("User can change only their own password");
        }

        if (signedinUser.isLdap() || signedinUser.isOauthGoogle() || signedinUser.isOidc()) {
            return forbidden("It's not possible to change the password of LDAP, Google sign-in or OIDC authenticated users.");
        }

        // Validate
        form = signinFormValidation.validateChangePassword(form);
        if (form.hasErrors()) return forbidden(form.errorsAsJson());

        // Check old password
        String oldPassword = form.get().getOldPassword();
        if (!authService.authenticate(signedinUser, oldPassword)) {
            form = form.withError(new ValidationError(ChangePasswordModel.OLD_PASSWORD, "Wrong password"));
            return forbidden(form.errorsAsJson());
        }

        // Change password
        String newPassword = form.get().getNewPassword();
        userService.updatePassword(signedinUser, newPassword);

        return ok();
    }

    /**
     * POST request to delete a user. Is called from user manager and user
     * profile.
     * <p>
     * It can't be a HTTP DELETE because it contains form data and Play doesn't
     * handle body data in a DELETE request.
     */
    @Transactional
    @Auth
    public Result remove(String usernameOfUserToRemove) throws ForbiddenException, NotFoundException, IOException {
        User signedinUser = authService.getSignedinUser();
        String normalizedSignedinUsername = signedinUser.getUsername();
        String normalizedUsernameOfUserToRemove = User.normalizeUsername(usernameOfUserToRemove);
        if (!signedinUser.isAdmin() && !normalizedUsernameOfUserToRemove.equals(normalizedSignedinUsername)) {
            return forbidden("You are not allowed to delete this user.");
        }

        DynamicForm requestData = formFactory.form().bindFromRequest();
        switch (signedinUser.getAuthMethod()) {
            case DB:
            case LDAP:
                try {
                    String password = requestData.get("password");
                    if (!authService.authenticate(signedinUser, password)) {
                        return forbidden(Json.mapper().readTree("{\"password\": [\"wrong password\"]}"));
                    }
                } catch (NamingException e) {
                    LOGGER.warn("LDAP problems - " + e);
                    return internalServerError("Problems with LDAP. Ask your admin.");
                }
                break;
            case OIDC:
            case ORCID:
            case SRAM:
            case CONEXT:
            case OAUTH_GOOGLE:
                // Google OAuth, OIDC, ORCID, SRAM and CONEXT users confirm with their username
                String username = requestData.get("username");
                if (!username.equals(signedinUser.getUsername())) {
                    return forbidden("Wrong username");
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown auth method");
        }

        userService.removeUser(normalizedUsernameOfUserToRemove);

        // If the user removes himself: sign out by removing the session cookie
        if (normalizedUsernameOfUserToRemove.equals(normalizedSignedinUsername)) {
            return ok(" ").withNewSession();
        } else {
            return ok();
        }
    }

    private void checkUsernameIsOfSignedinUser(String normalizedUsername, User signedinUser) throws JatosGuiException {
        if (!normalizedUsername.equals(signedinUser.getUsername())) {
            ForbiddenException e = new ForbiddenException("You are not allowed to get data for user \"" + normalizedUsername + "\".");
            jatosGuiExceptionThrower.throwRedirect(e, controllers.gui.routes.Home.home());
        }
    }

}
