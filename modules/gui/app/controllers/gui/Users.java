package controllers.gui;

import auth.gui.AuthAction.Auth;
import auth.gui.AuthService;
import com.fasterxml.jackson.databind.node.ArrayNode;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import daos.common.UserDao;
import exceptions.gui.*;
import models.common.User;
import models.common.User.Role;
import models.gui.ChangePasswordProperties;
import models.gui.NewUserProperties;
import models.gui.UserProperties;
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
import java.util.Collections;
import java.util.List;

import static controllers.gui.actionannotations.SaveLastVisitedPageUrlAction.SaveLastVisitedPageUrl;
import static models.gui.ChangePasswordProperties.*;
import static services.gui.UserService.ADMIN_USERNAME;

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
    private final BreadcrumbsService breadcrumbsService;
    private final FormFactory formFactory;
    private final JsonUtils jsonUtils;

    @Inject
    Users(JatosGuiExceptionThrower jatosGuiExceptionThrower,
            UserDao userDao, UserService userService,
            AuthService authService,
            BreadcrumbsService breadcrumbsService, FormFactory formFactory,
            JsonUtils jsonUtils) {
        this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
        this.userDao = userDao;
        this.userService = userService;
        this.authService = authService;
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
    public Result allUserData() throws IOException {
        List<User> userList = userDao.findAll();

        ArrayNode allUserData = userList.stream()
                .map(jsonUtils::getSingleUserData)
                .collect(Json.mapper()::createArrayNode, ArrayNode::add, ArrayNode::addAll);

        return ok(allUserData);
    }

    /**
     * POST request to add or remove a role from a user.
     */
    @Transactional
    @Auth(Role.ADMIN)
    public Result toggleRole(Http.Request request, String role, boolean value) {
        DynamicForm requestData = formFactory.form().bindFromRequest(request);
        String usernameOfUserToChange = requestData.get("username");
        if (usernameOfUserToChange == null) return badRequest("Missing username");
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
        return ok(jsonUtils.getSingleUserData(signedinUser));
    }

    /**
     * Handles POST request of user create form. Only users with Role ADMIN are
     * allowed to create new users.
     */
    @Transactional
    @Auth(Role.ADMIN)
    public Result create(Http.Request request) throws ForbiddenException {
        Form<NewUserProperties> form = formFactory.form(NewUserProperties.class).bindFromRequest(request);
        if (form.hasErrors()) return badRequest(form.errorsAsJson());
        User newUser = userService.registerUser(form.get());
        return ok(jsonUtils.getSingleUserData(newUser));
    }

    /**
     * Handles POST request of user edit profile form (except password and roles).
     * This POST can come from the user themselves or from an admin user to edit another user.
     */
    @Transactional
    @Auth
    public Result edit(Http.Request request) throws JatosGuiException {
        User signedinUser = authService.getSignedinUser();
        DynamicForm requestData = formFactory.form().bindFromRequest(request);
        String username = requestData.get("username");
        if (username == null) return badRequest("Missing username");
        String normalizedUsernameOfUserToChange = User.normalizeUsername(username);
        User user = userDao.findByUsername(normalizedUsernameOfUserToChange);
        if (user == null) return badRequest("User doesn't exist");

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

        Form<UserProperties> form = formFactory.form(UserProperties.class).bindFromRequest(request);
        if (form.hasErrors()) return badRequest(form.errorsAsJson());

        user.setName(form.get().getName());
        user.setEmail(form.get().getEmail());
        userDao.update(user);
        return ok();
    }

    /**
     * Handles POST request to change a password originated in the user manager and initiated by an admin
     */
    @Transactional
    @Auth(Role.ADMIN)
    public Result changePasswordByAdmin(Http.Request request) throws NamingException {
        DynamicForm dynForm = formFactory.form().bindFromRequest(request);
        ChangePasswordProperties props = new ChangePasswordProperties();
        props.setUsername(dynForm.get(USERNAME));
        props.setNewPassword(dynForm.get(NEW_PASSWORD));
        props.setNewPasswordRepeat(dynForm.get(NEW_PASSWORD)); // Admins do not have to repeat the password
        props.setOldPassword(dynForm.get(OLD_PASSWORD));

        User user = userDao.findByUsername(props.getUsername());
        if (user == null) {
            return badRequest("User doesn't exist.");
        }
        if (!user.isDb()) {
            return forbidden("It's not possible to change the password of a user that is not authenticated locally.");
        }

        // Only the user 'admin' is allowed to change their own password
        User signedinUser = authService.getSignedinUser();
        if (props.getUsername().equals(ADMIN_USERNAME) && !signedinUser.getUsername().equals(ADMIN_USERNAME)) {
            return forbidden("You are not allowed to change the password of user 'admin'.");
        }

        Form<ChangePasswordProperties> form = formFactory.form(ChangePasswordProperties.class).fill(props);
        List<ValidationError> errors = props.validate();
        if (errors != null && !errors.isEmpty()) {
            for (ValidationError e : errors) {
                form = form.withError(e.key(), e.message());
            }
            return badRequest(form.errorsAsJson());
        }

        userService.updatePassword(user, props.getNewPassword());
        return ok();
    }

    /**
     * Handles POST request to change a password form initiated by a user themselves
     */
    @Transactional
    @Auth
    public Result changePasswordByUser(Http.Request request) throws NamingException {
        DynamicForm dynForm = formFactory.form().bindFromRequest(request);
        ChangePasswordProperties props = new ChangePasswordProperties();
        props.setUsername(dynForm.get(USERNAME));
        props.setNewPassword(dynForm.get(NEW_PASSWORD));
        props.setNewPasswordRepeat(dynForm.get(NEW_PASSWORD_REPEAT)); // Admins do not have to repeat the password
        props.setOldPassword(dynForm.get(OLD_PASSWORD));

        User signedinUser = authService.getSignedinUser();
        if (!signedinUser.getUsername().equals(props.getUsername())) {
            return forbidden("User can change only their own password");
        }
        if (!signedinUser.isDb()) {
            return forbidden("It's not possible to change the password of a user that is not authenticated locally.");
        }

        // Validate
        Form<ChangePasswordProperties> form = formFactory.form(ChangePasswordProperties.class).bind(dynForm.rawData());
        if (form.hasErrors()) return badRequest(form.errorsAsJson());

        // Check old password against current password in the database
        if (!authService.authenticate(signedinUser, props.getOldPassword())) {
            return forbidden(JsonUtils.asJsonNode(Collections.singletonMap(OLD_PASSWORD, Collections.singletonList("Wrong password"))));
        }

        userService.updatePassword(signedinUser, props.getNewPassword());
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
    public Result remove(Http.Request request) throws ForbiddenException, NotFoundException, IOException {
        User signedinUser = authService.getSignedinUser();
        String normalizedSignedinUsername = signedinUser.getUsername();
        DynamicForm requestData = formFactory.form().bindFromRequest(request);
        String usernameOfUserToRemove = requestData.get("username");
        if (usernameOfUserToRemove == null) return badRequest("Missing username");
        String normalizedUsernameOfUserToRemove = User.normalizeUsername(usernameOfUserToRemove);
        if (!signedinUser.isAdmin() && !normalizedUsernameOfUserToRemove.equals(normalizedSignedinUsername)) {
            return forbidden("You are not allowed to delete this user.");
        }

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
                String confirmUsername = requestData.get("confirmUsername");
                if (confirmUsername == null || !confirmUsername.equals(signedinUser.getUsername())) {
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
