package auth.gui;

import actions.common.AsyncAction.Executor;
import auth.gui.AuthAction.Auth;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.base.Strings;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import daos.common.ApiTokenDao;
import general.common.Http.Context;
import models.common.ApiToken;
import models.common.User;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import services.gui.ApiTokenService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

import static actions.common.AsyncAction.Async;
import static auth.gui.AuthAction.SIGNEDIN_USER;

/**
 * All JATOS GUI endpoints concerning API tokens (personal access tokens)
 *
 * @author Kristian Lange
 */
@Singleton
public class ApiTokens extends Controller {

    private final ApiTokenDao apiTokenDao;
    private final ApiTokenService apiTokenService;

    @Inject
    ApiTokens(ApiTokenDao apiTokenDao,
              ApiTokenService apiTokenService) {
        this.apiTokenDao = apiTokenDao;
        this.apiTokenService = apiTokenService;
    }

    @Async(Executor.IO)
    @Auth
    public Result allTokenDataByUser() {
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        List<ApiToken> tokenList = apiTokenDao.findByUser(signedinUser);
        ArrayNode tokenData = Json.newArray();
        for (ApiToken token : tokenList) {
            tokenData.add(Json.mapper().valueToTree(token));
        }
        JsonNode data = Json.newObject().set("data", tokenData);
        return ok(data);
    }

    @Async(Executor.IO)
    @Auth
    public Result generate(String name, Integer expires) {
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        if (Strings.isNullOrEmpty(name)) return badRequest("Name must not be empty");
        if (!Jsoup.isValid(name, Safelist.none())) return badRequest("No HTML allowed");
        if (expires == null || expires < 0) return badRequest("Expiration must be >= 0");
        Integer expiresWithNull = expires == 0 ? null : expires; // 0 => null and means the token never expires
        String apiTokenStr = apiTokenService.create(signedinUser, name, expiresWithNull);
        return ok(apiTokenStr);
    }

    @Async(Executor.IO)
    @Auth
    public Result remove(Long id) {
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        ApiToken token = apiTokenDao.find(id);
        if (token == null || token.getUser() != signedinUser) return notFound("Token doesn't exist");
        apiTokenDao.remove(token);
        return ok();
    }

    @Async(Executor.IO)
    @Auth
    public Result toggleActive(Long id, Boolean active) {
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        ApiToken token = apiTokenDao.find(id);
        if (token == null || token.getUser() != signedinUser) return notFound("Token doesn't exist");
        token.setActive(active);
        apiTokenDao.merge(token);
        return ok(" ");
    }

}