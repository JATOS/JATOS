package controllers.gui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.base.Strings;
import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import daos.common.ApiTokenDao;
import models.common.ApiToken;
import models.common.User;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import play.db.jpa.Transactional;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import services.gui.AuthenticationService;
import services.gui.ApiTokenService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

/**
 * All JATOS GUI endpoints concerning API tokens (personal access tokens)
 *
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
@GuiAccessLogging
@Singleton
public class ApiTokens extends Controller {

    private final ApiTokenDao apiTokenDao;
    private final ApiTokenService apiTokenService;
    private final AuthenticationService authenticationService;

    @Inject
    ApiTokens(ApiTokenDao apiTokenDao, ApiTokenService apiTokenService, AuthenticationService authenticationService) {
        this.apiTokenDao = apiTokenDao;
        this.apiTokenService = apiTokenService;
        this.authenticationService = authenticationService;
    }

    @Transactional
    @Authenticated
    public Result allTokenDataByUser() {
        User loggedInUser = authenticationService.getLoggedInUser();
        List<ApiToken> tokenList = apiTokenDao.findByUser(loggedInUser);
        ArrayNode tokenData = Json.newArray();
        for (ApiToken token : tokenList) {
            tokenData.add(Json.mapper().valueToTree(token));
        }
        JsonNode data = Json.newObject().set("data", tokenData);
        return ok(data);
    }

    @Transactional
    @Authenticated
    public Result generate(String name, Integer expires) {
        User loggedInUser = authenticationService.getLoggedInUser();
        if (Strings.isNullOrEmpty(name)) return badRequest("Name must not be empty");
        if (!Jsoup.isValid(name, Safelist.none())) return badRequest("No HTML allowed");
        if (expires == null || expires < 0) return badRequest("Expiration must be >= 0");
        expires = expires == 0 ? null : expires;
        String apiTokenStr = apiTokenService.create(loggedInUser, name, expires);
        return ok(apiTokenStr);
    }

    @Transactional
    @Authenticated
    public Result remove(Long id) {
        User loggedInUser = authenticationService.getLoggedInUser();
        ApiToken token = apiTokenDao.find(id);
        if (token == null || token.getUser() != loggedInUser) return notFound("Token doesn't exist");
        apiTokenDao.remove(token);
        return ok();
    }

    @Transactional
    @Authenticated
    public Result toggleActive(Long id, Boolean active) {
        User loggedInUser = authenticationService.getLoggedInUser();
        ApiToken token = apiTokenDao.find(id);
        if (token == null || token.getUser() != loggedInUser) return notFound("Token doesn't exist");
        token.setActive(active);
        apiTokenDao.update(token);
        return ok(" ");
    }

}