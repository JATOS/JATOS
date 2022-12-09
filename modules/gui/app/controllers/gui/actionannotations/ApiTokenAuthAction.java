package controllers.gui.actionannotations;

import daos.common.ApiTokenDao;
import general.common.RequestScope;
import models.common.ApiToken;
import models.common.User;
import play.db.jpa.JPAApi;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.With;
import services.gui.AuthenticationService;
import utils.common.HashUtils;
import utils.common.Helpers;

import javax.inject.Inject;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static controllers.gui.actionannotations.ApiTokenAuthAction.ApiTokenAuth;

/**
 * Defines the ApiTokenAuth annotation that is used to authorize JATOS API calls
 */
@SuppressWarnings("deprecation")
public class ApiTokenAuthAction extends Action<ApiTokenAuth> {

    @With(ApiTokenAuthAction.class)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ApiTokenAuth {
        User.Role value() default User.Role.USER;
    }

    public static final String API_TOKEN = "apiToken";

    private final ApiTokenDao apiTokenDao;

    private final JPAApi jpa;

    @Inject
    ApiTokenAuthAction(ApiTokenDao apiTokenDao, JPAApi jpa) {
        this.apiTokenDao = apiTokenDao;
        this.jpa = jpa;
    }

    public CompletionStage<Result> call(Http.Context ctx) {

        // Check proper authorization header is there
        if (!Helpers.isApiRequest(ctx.request())) {
            return CompletableFuture.completedFuture(unauthorized("Bearer authorization header missing"));
        }

        // Check token checksum
        //noinspection OptionalGetWithoutIsPresent - it's checked in Helpers.isApiRequest
        String headerValue = ctx.request().header("Authorization").get();
        String fullTokenStr = headerValue.replace("Bearer", "").trim();
        String cleanedToken = fullTokenStr.replace("jap_", "").substring(0, 31);
        String calculatedChecksum = HashUtils.getChecksum(cleanedToken);
        String givenChecksum = fullTokenStr.substring(fullTokenStr.length() - 6);
        if (!givenChecksum.equals(calculatedChecksum)) {
            return CompletableFuture.completedFuture(unauthorized("Invalid api token"));
        }

        // Search token by its hash in the database
        String tokenHash = HashUtils.getHash(fullTokenStr, HashUtils.SHA_256);
        Optional<ApiToken> apiTokenOptional = jpa.withTransaction(() -> apiTokenDao.findByHash(tokenHash));
        if (!apiTokenOptional.isPresent()) {
            return CompletableFuture.completedFuture(unauthorized("Invalid api token"));
        }
        ApiToken apiToken = apiTokenOptional.get();

        // Tokens can be deactivated
        if (!apiToken.isActive()) {
            return CompletableFuture.completedFuture(unauthorized("Invalid api token"));
        }

        // Check the token's user: since these are personal access tokens and the user which belongs to the token can
        // be deactivated too
        User user = apiToken.getUser();
        RequestScope.put(AuthenticationService.LOGGED_IN_USER, user);
        if (!user.isActive()) {
            return CompletableFuture.completedFuture(unauthorized("Invalid api token"));
        }

        // Check authorization
        User.Role neededRole = configuration.value();
        if (!user.hasRole(neededRole)) {
            return CompletableFuture.completedFuture(unauthorized("Invalid api token"));
        }

        // Check if token is expired
        if (apiToken.isExpired()) {
            return CompletableFuture.completedFuture(unauthorized("Invalid api token"));
        }

        RequestScope.put(API_TOKEN, apiToken);
        return delegate.call(ctx);
    }

}
