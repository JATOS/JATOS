package auth.gui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.base.Strings;
import auth.gui.AuthAction.Auth;
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
    private final AuthService authService;

    @Inject
    ApiTokens(ApiTokenDao apiTokenDao, ApiTokenService apiTokenService, AuthService authService) {
        this.apiTokenDao = apiTokenDao;
        this.apiTokenService = apiTokenService;
        this.authService = authService;
    }

    @Transactional
    @Auth
    public Result allTokenDataByUser() {
        User signedinUser = authService.getSignedinUser();
        List<ApiToken> tokenList = apiTokenDao.findByUser(signedinUser);
        ArrayNode tokenData = Json.newArray();
        for (ApiToken token : tokenList) {
            tokenData.add(Json.mapper().valueToTree(token));
        }
        JsonNode data = Json.newObject().set("data", tokenData);
        return ok(data);
    }

    @Transactional
    @Auth
    public Result generate(String name, Integer expires) {
        User signedinUser = authService.getSignedinUser();
        if (Strings.isNullOrEmpty(name)) return badRequest("Name must not be empty");
        if (!Jsoup.isValid(name, Safelist.none())) return badRequest("No HTML allowed");
        if (expires == null || expires < 0) return badRequest("Expiration must be >= 0");
        expires = expires == 0 ? null : expires;
        String apiTokenStr = apiTokenService.create(signedinUser, name, expires);
        return ok(apiTokenStr);
    }

    @Transactional
    @Auth
    public Result remove(Long id) {
        User signedinUser = authService.getSignedinUser();
        ApiToken token = apiTokenDao.find(id);
        if (token == null || token.getUser() != signedinUser) return notFound("Token doesn't exist");
        apiTokenDao.remove(token);
        return ok();
    }

    @Transactional
    @Auth
    public Result toggleActive(Long id, Boolean active) {
        User signedinUser = authService.getSignedinUser();
        ApiToken token = apiTokenDao.find(id);
        if (token == null || token.getUser() != signedinUser) return notFound("Token doesn't exist");
        token.setActive(active);
        apiTokenDao.update(token);
        return ok(" ");
    }

}