package auth.gui;

import auth.gui.AuthAction.Auth;
import auth.gui.AuthAction.IAuth.AuthResult;
import general.gui.RequestScopeMessaging;
import models.common.User;
import models.common.User.Role;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.With;
import utils.common.Helpers;

import javax.inject.Inject;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * This class defines the @Auth annotation used in JATOS GUI and JATOS API. Authentication methods are 1) via session
 * cookies, and 2) via personal access tokens (API tokens).
 * <p>
 * The actual session cookie auth is done in {@link AuthActionSessionCookie}. The actual auth with API tokens is in
 * {@link AuthActionApiToken}.
 * <p>
 * Additionally, it does some basic authorization (with {@link User.Role}).
 * <p>
 * Authentication via DB, OIDC and Google Sign-In use the session cookie authentication.
 * <p>
 * IMPORTANT: Since this annotation accesses the database the annotated method has to be within a transaction.
 * This means the @Transactional annotation has to be BEFORE the @Auth annotation.
 * annotation.
 *
 * @author Kristian Lange
 */
public class AuthAction extends Action<Auth> {

    /**
     * This @Auth annotation can be used on every controller action (GUI or API) where authentication
     * and authorization is required. If no Role is added than the default Role 'USER' is assumed.
     */
    @With(AuthAction.class)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Auth {
        Role value() default Role.USER;
    }

    /**
     * Interface that every authentication method has to implement.
     */
    interface IAuth {

        /**
         * @param request       This action's {@link Http.Request} object
         * @param necessaryRole Role the user must have to access the resource
         * @return Returns a {@link Result} only if auth was unsuccessful. Otherwise, the Optional is empty.
         */
        AuthResult authenticate(Http.Request request, User.Role necessaryRole);

        /**
         * Result for an auth attempt. If 'authenticated' is true it is authenticated. If 'authenticated' is false and
         * 'error' is null the authentication method was considered not suitable and other methods can be tried. If
         * 'authenticated' is false and 'error' contains a Result the authentication failed and the Result will be returned.
         */
        class AuthResult {
            boolean authenticated;
            Result error = null;

            static AuthResult of(boolean authenticated) {
                return new AuthResult(authenticated);
            }

            static AuthResult of(Result error) {
                return new AuthResult(error);
            }

            private AuthResult(boolean authenticated) {
                this.authenticated = authenticated;
            }

            private AuthResult(Result error) {
                this.authenticated = false;
                this.error = error;
            }
        }
    }

    List<IAuth> authMethods = new ArrayList<>();

    @Inject
    AuthAction(AuthActionSessionCookie authViaSessionCookie, AuthActionApiToken apiTokenAuth) {
        authMethods.add(authViaSessionCookie);
        authMethods.add(apiTokenAuth);
    }

    public CompletionStage<Result> call(Http.Request request) {
        User.Role necessaryRole = configuration.value();

        for (IAuth authMethod : authMethods) {
            AuthResult authResult = authMethod.authenticate(request, necessaryRole);
            if (authResult.authenticated) {
                return delegate.call(request); // successful authentication
            } else if (authResult.error != null) {
                return CompletableFuture.completedFuture(authResult.error);
            }
        }

        if (Helpers.isHtmlRequest(request)) {
            RequestScopeMessaging.error("Failed authentication");
            return CompletableFuture.completedFuture(redirect(auth.gui.routes.SignIn.login()));
        } else {
            return CompletableFuture.completedFuture(forbidden("Failed authentication"));
        }

    }

}

