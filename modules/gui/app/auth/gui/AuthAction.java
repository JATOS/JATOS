package auth.gui;

import auth.gui.AuthAction.Auth;
import auth.gui.AuthAction.AuthMethod.AuthResult;
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
import java.util.function.Function;

/**
 * This class defines the {@link Auth} annotation used in JATOS GUI and JATOS API. Authentication methods are
 * 1) via session cookies, and 2) via personal access tokens (API tokens).
 * <p>
 * The actual session cookie authentication is done in {@link AuthSessionCookie}. The actual authentication with API
 * tokens is in {@link AuthApiToken}.
 * <p>
 * Additionally, it does some basic authorization (with {@link User.Role}).
 * <p>
 * Authentication via DB, LDAP, OIDC and Google Sign-In use the session cookie authentication.
 * <p>
 * IMPORTANT: Since this annotation accesses the database, the annotated method has to be within a transaction.
 * This means the {@link javax.transaction.Transactional} annotation has to be BEFORE the {@link Auth} annotation.
 *
 * @author Kristian Lange
 */
public class AuthAction extends Action<Auth> {

    /**
     * This @Auth annotation can be used on every controller action (GUI or API) where authentication
     * and authorization are required. If no Role is added, then the default Role 'USER' is assumed.
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
    interface AuthMethod {

        /**
         * @param request       This action's {@link Http.Request} object
         * @param necessaryRole Role the user must have to access the resource
         * @return Returns an {@link AuthResult}.
         */
        AuthResult authenticate(Http.Request request, User.Role necessaryRole);

        /**
         * Result of an authentication attempt.
         */
        class AuthResult {

            /**
             * An AuthResult always has a state. It can be either
             * 1) AUTHENTICATED for a successful authentication,
             * 2) DENIED for an unsuccessful authentication, or
             * 3) WRONG_METHOD if the authentication method does not apply (e.g., if there is no session cookie in a
             * session cookie authentication).
             */
            enum State {AUTHENTICATED, DENIED, WRONG_METHOD}

            State state;

            /**
             * The AuthResult can contain a {@link Result} that is sent instead of the original action's result.
             */
            Result result;

            /**
             * The AuthResult can have a function that is called on the result of the original action.
             */
            Function<Result, Result> postHook = r -> r; // Default: do nothing

            private AuthResult(State state) {
                this.state = state;
            }

            static AuthResult authenticated() {
                return new AuthResult(State.AUTHENTICATED);
            }

            static AuthResult authenticated(Function<Result, Result> postHook) {
                AuthResult ar =  new AuthResult(State.AUTHENTICATED);
                ar.postHook = postHook;
                return ar;
            }

            static AuthResult wrongMethod() {
                return new AuthResult(State.WRONG_METHOD);
            }

            static AuthResult denied(Result result) {
                AuthResult ar = new AuthResult(State.DENIED);
                ar.result = result;
                return ar;
            }
        }
    }

    private final List<AuthMethod> authMethods = new ArrayList<>();

    @Inject
    AuthAction(AuthSessionCookie authSessionCookie, AuthApiToken apiTokenAuth) {
        authMethods.add(authSessionCookie);
        authMethods.add(apiTokenAuth);
    }

    public CompletionStage<Result> call(Http.Request request) {
        User.Role necessaryRole = configuration.value();

        // Try to authenticate with each registered method
        for (AuthMethod authMethod : authMethods) {

            AuthResult authResult = authMethod.authenticate(request, necessaryRole);
            switch (authResult.state) {
                case AUTHENTICATED:
                    return delegate.call(request).thenApply(authResult.postHook); // Successful authentication
                case DENIED:
                    return CompletableFuture.completedFuture(authResult.result);
                case WRONG_METHOD:
                    // Do nothing and try the next auth method
            }
        }

        // No authentication method worked
        return denied(request);
    }

    static CompletionStage<Result> denied(Http.Request request) {
        if (Helpers.isHtmlRequest(request)) {
            RequestScopeMessaging.error("Failed authentication");
            return CompletableFuture.completedFuture(redirect(auth.gui.routes.SignIn.login()));
        } else {
            return CompletableFuture.completedFuture(forbidden("Failed authentication"));
        }
    }

}

