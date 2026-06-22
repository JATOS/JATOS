package controllers.gui;

import actions.common.AsyncAction.Async;
import actions.common.AsyncAction.Executor;
import auth.gui.AuthAction.Auth;
import auth.gui.AuthService;
import com.fasterxml.jackson.databind.node.ArrayNode;
import daos.common.UserDao;
import exceptions.common.AuthException;
import http.common.Http.Context;
import json.common.DefaultJson;
import json.common.JsonUtils;
import models.common.User;
import models.common.User.Role;
import models.gui.ChangePasswordProperties;
import models.gui.NewUserProperties;
import models.gui.UserProperties;
import play.data.DynamicForm;
import play.data.Form;
import play.data.FormFactory;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.BreadcrumbsService;
import services.gui.UserService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.List;

import static auth.gui.AuthAction.SIGNEDIN_USER;
import static controllers.gui.actionannotations.SaveLastVisitedPageUrlAction.SaveLastVisitedPageUrl;
import static models.common.User.Role.*;
import static models.gui.ChangePasswordProperties.*;
import static services.gui.UserService.ADMIN_USERNAME;

/**
 * Controller with actions concerning users (incl. user manager)
 */
@Singleton
public class Users extends Controller {

    private final UserDao userDao;
    private final UserService userService;
    private final AuthService authService;
    private final BreadcrumbsService breadcrumbsService;
    private final FormFactory formFactory;
    private final DefaultJson defaultJson;
    private final JsonUtils jsonUtils;

    @Inject
    Users(UserDao userDao,
          UserService userService,
          AuthService authService,
          BreadcrumbsService breadcrumbsService,
          FormFactory formFactory,
          DefaultJson defaultJson,
          JsonUtils jsonUtils) {
        this.userDao = userDao;
        this.userService = userService;
        this.authService = authService;
        this.breadcrumbsService = breadcrumbsService;
        this.formFactory = formFactory;
        this.defaultJson = defaultJson;
        this.jsonUtils = jsonUtils;
    }

    @Async(Executor.IO)
    @Auth(roles = ADMIN)
    @SaveLastVisitedPageUrl
    public Result userManager(Http.Request request) {
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        String breadcrumbs = breadcrumbsService.generateForAdministration(BreadcrumbsService.USER_MANAGER);
        return ok(views.html.gui.admin.userManager.render(signedinUser, breadcrumbs, request.asScala()));
    }

    /**
     * GET request: Returns a list of all users as JSON
     */
    @Async(Executor.IO)
    @Auth(roles = ADMIN)
    public Result allUserData() {
        List<User> userList = userDao.findAll();

        ArrayNode allUserData = userList.stream()
                .map(jsonUtils::getSingleUserData)
                .collect(Json.mapper()::createArrayNode, ArrayNode::add, ArrayNode::addAll);

        return ok(allUserData);
    }

    /**
     * POST a request to add or remove a role from a user.
     */
    @Async(Executor.IO)
    @Auth(roles = ADMIN)
    public Result toggleRole(Http.Request request, String role, boolean value) {
        DynamicForm requestData = formFactory.form().bindFromRequest(request);
        String usernameOfUserToChange = requestData.get("username");
        if (usernameOfUserToChange == null) return badRequest("Missing username");
        String normalizedUsernameOfUserToChange = User.normalizeUsername(usernameOfUserToChange);
        switch (Role.valueOf(role)) {
            case SUPERUSER:
                return ok(defaultJson.objAsJsonNode(
                        userService.changeSuperuserRole(normalizedUsernameOfUserToChange, value)));
            case ADMIN:
                return ok(defaultJson.objAsJsonNode(
                        userService.changeAdminRole(normalizedUsernameOfUserToChange, value)));
            default:
                return badRequest("Unknown role");
        }
    }

    /**
     * GET request that returns user data of the signed-in user
     */
    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER, ADMIN})
    public Result signedinUserData() {
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        return ok(jsonUtils.getSingleUserData(signedinUser));
    }

    /**
     * Handles POST request of user create form. Only users with Role ADMIN are allowed to create new users.
     */
    @Async(Executor.IO)
    @Auth(roles = ADMIN)
    public Result create(Http.Request request) {
        Form<NewUserProperties> form = formFactory.form(NewUserProperties.class).bindFromRequest(request);
        if (form.hasErrors()) return badRequest(form.errorsAsJson());
        User newUser = userService.registerUser(form.get());
        return ok(jsonUtils.getSingleUserData(newUser));
    }

    /**
     * Handles POST request of user edit profile form (except password and roles). This POST can come from the user
     * themselves or from an admin user to edit another user.
     */
    @Async(Executor.IO)
    @Auth(roles = {USER, ADMIN})
    public Result edit(Http.Request request) {
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        DynamicForm requestData = formFactory.form().bindFromRequest(request);
        String username = requestData.get("username");
        String normalizedUsernameOfUserToChange = User.normalizeUsername(username);
        User user = userDao.findByUsername(normalizedUsernameOfUserToChange);
        if (user == null) return badRequest("User doesn't exist");

        if (user.isOauthGoogle() || user.isOidc() || user.isOrcid() || user.isSram() || user.isConext()) {
            return forbidden(user.getAuthMethod() + " authenticated users can't have their profile changed.");
        }

        if (!signedinUser.isAdmin() && !normalizedUsernameOfUserToChange.equals(signedinUser.getUsername())) {
            return forbidden("You are not allowed to get data for this user.");
        }

        Form<UserProperties> form = formFactory.form(UserProperties.class).bindFromRequest(request);
        if (form.hasErrors()) return badRequest(form.errorsAsJson());

        // Update user in the database: so far it's only the user's name
        user.setName(form.get().getName());
        user.setEmail(form.get().getEmail());
        userDao.merge(user);
        return ok();
    }

    /**
     * Handles POST request to change a password originated in the user manager and initiated by an admin
     */
    @Async(Executor.IO)
    @Auth(roles = ADMIN)
    public Result changePasswordByAdmin(Http.Request request) {
        DynamicForm dynForm = formFactory.form().bindFromRequest(request);
        ChangePasswordProperties props = new ChangePasswordProperties();
        props.setUsername(dynForm.get(USERNAME));
        props.setNewPassword(dynForm.get(NEW_PASSWORD));
        props.setNewPasswordRepeat(dynForm.get(NEW_PASSWORD)); // Admins do not have to repeat the password
        props.setOldPassword(dynForm.get(OLD_PASSWORD));

        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        User user = userDao.findByUsername(props.getUsername());
        if (user == null) {
            return badRequest("User doesn't exist.");
        }
        if (!user.isDb()) {
            return forbidden("It's not possible to change the password of a user that is not authenticated locally.");
        }

        // Only the user 'admin' is allowed to change their own password
        if (props.getUsername().equals(ADMIN_USERNAME) && !signedinUser.getUsername().equals(ADMIN_USERNAME)) {
            return forbidden("You are not allowed to change the password of user 'admin'.");
        }

        Form<ChangePasswordProperties> form = formFactory.form(ChangePasswordProperties.class).fill(props);
        form = UserService.validateAndAddErrors(form, props);
        if (form.hasErrors()) return badRequest(form.errorsAsJson());

        userService.updatePassword(user, props.getNewPassword());
        return ok();
    }

    /**
     * Handles POST request to change a password form initiated by a user themselves
     */
    @Async(Executor.IO)
    @Auth(roles = {USER, ADMIN})
    public Result changePasswordByUser(Http.Request request) {
        DynamicForm dynForm = formFactory.form().bindFromRequest(request);
        ChangePasswordProperties props = new ChangePasswordProperties();
        props.setUsername(dynForm.get(USERNAME));
        props.setNewPassword(dynForm.get(NEW_PASSWORD));
        props.setNewPasswordRepeat(dynForm.get(NEW_PASSWORD_REPEAT));
        props.setOldPassword(dynForm.get(OLD_PASSWORD));

        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        if (!signedinUser.getUsername().equals(props.getUsername())) {
            return forbidden("User can change only their own password");
        }
        if (!signedinUser.isDb()) {
            return forbidden("It's not possible to change the password of a user that is not authenticated locally.");
        }

        // Validate
        Form<ChangePasswordProperties> form = formFactory.form(ChangePasswordProperties.class).fill(props);
        form = UserService.validateAndAddErrors(form, props);
        if (form.hasErrors()) return badRequest(form.errorsAsJson());

        // Check old password against current password in the database
        if (!authService.authenticate(signedinUser, props.getOldPassword())) {
            return forbidden(defaultJson.objAsJsonNode(Collections.singletonMap(OLD_PASSWORD, Collections.singletonList("Wrong password"))));
        }

        userService.updatePassword(signedinUser, props.getNewPassword());
        return ok();
    }

    /**
     * POST request to delete a user. Is called from user manager and user profile.
     *
     * It can't be a HTTP DELETE because it contains form data and Play doesn't handle body data in a DELETE request.
     */
    @Async(Executor.IO)
    @Auth(roles = {USER, ADMIN})
    public Result remove(Http.Request request) {
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
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
                String password = requestData.get("password");
                if (!authService.authenticate(signedinUser, password)) {
                    return forbidden(Json.newObject().putArray("password").add("wrong password"));
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
                throw new AuthException("Unknown auth method");
        }

        userService.removeUser(normalizedUsernameOfUserToRemove);

        // If the user removes himself: sign out by removing the session cookie
        if (normalizedUsernameOfUserToRemove.equals(normalizedSignedinUsername)) {
            Context.current().response().clearSession();
            return ok(" ");
        } else {
            return ok();
        }
    }

}
