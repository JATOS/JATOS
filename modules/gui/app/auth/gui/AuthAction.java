package auth.gui;

import auth.gui.AuthAction.Auth;
import auth.gui.AuthAction.AuthMethod.AuthResult;
import auth.gui.AuthAction.AuthMethod.Type;
import exceptions.common.AuthException;
import http.common.Http.Context;
import http.common.HttpUtils;
import models.common.User;
import models.common.User.Role;
import play.libs.typedmap.TypedKey;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.With;

import javax.inject.Inject;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static messaging.common.FlashMessagingHelper.ERROR;

/**
 * This class defines the {@link Auth} annotation used in JATOS GUI and JATOS API. Authentication methods are
 * 1) via session cookies, and 2) via personal access tokens (API tokens).
 *
 * The actual session cookie authentication is done in {@link AuthSessionCookie}. The actual authentication with API
 * tokens is in {@link AuthApiToken}.
 *
 * Additionally, it does some basic authorization (with {@link User.Role}).
 *
 * Authentication via DB, LDAP, OIDC and Google Sign-In use the session cookie authentication.
 *
 * IMPORTANT: Since this annotation accesses the database, the annotated method has to be within a transaction.
 * This means the {@link javax.transaction.Transactional} annotation has to be BEFORE the {@link Auth} annotation.
 */
public class AuthAction extends Action<Auth> {

    /**
     * This @Auth annotation can be used on every controller action (GUI or API) where authentication
     * and authorization are required. If no Role is added, then the default Role 'NONE' is assumed.
     */
    @With(AuthAction.class)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Auth {
        Role[] roles() default {Role.NONE};
        Type[] types() default {Type.SESSION};
    }

    /**
     * Key name used in {@link Context} to store the signed-in User
     */
    public static final TypedKey<User> SIGNEDIN_USER = TypedKey.create("signedinUser");

    /**
     * Interface that every authentication method has to implement.
     */
    public interface AuthMethod {

        enum Type {
            SESSION, TOKEN
        }

        Type type();

        /**
         * @param allowedRoles   Roles that are allowed to access the resource
         * @return Returns an {@link AuthResult}.
         */
        AuthResult authenticate(EnumSet<Role> allowedRoles);

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

            private AuthResult(State state) {
                this.state = state;
            }

            static AuthResult authenticated() {
                return new AuthResult(State.AUTHENTICATED);
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
    AuthAction(AuthSessionCookie authSessionCookie, AuthApiToken authApiToken) {
        authMethods.add(authApiToken);
        authMethods.add(authSessionCookie);
    }

    public CompletionStage<Result> call(Http.Request request) {
        EnumSet<Role> allowedRoles = EnumSet.copyOf(Arrays.asList(configuration.roles()));
        EnumSet<Type> allowedTypes = EnumSet.copyOf(Arrays.asList(configuration.types()));

        // Try to authenticate with each registered method
        for (AuthMethod authMethod : authMethods) {

            if (!allowedTypes.contains(authMethod.type())) continue;

            AuthResult authResult = authMethod.authenticate(allowedRoles);
            switch (authResult.state) {
                case AUTHENTICATED:
                    return delegate.call(request); // Successful authentication
                case DENIED:
                    return CompletableFuture.completedFuture(authResult.result);
                case WRONG_METHOD:
                    // Do nothing and try the next auth method
            }
        }

        // No authentication method worked
        return denied();
    }

    static CompletionStage<Result> denied() {
        if (HttpUtils.isHtmlRequest()) {
            Context.current().response().putFlash(ERROR, "Failed authentication");
            return CompletableFuture.completedFuture(redirect(auth.gui.routes.Signin.signin()));
        } else {
            return CompletableFuture.failedStage(new AuthException("Failed authentication"));
        }
    }

}

