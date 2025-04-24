package auth.gui;

import general.common.Common;
import models.common.User;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Sign-in using SURFconext (surfconext.nl) based on OpenID Connect (OIDC).
 * <p>
 * Note that here it is not necessary to override {@link #getScope()}, because SURFconext ignores scopes other than openid.
 * Also see: https://servicedesk.surf.nl/wiki/spaces/IAM/pages/128909987/OpenID+Connect+features#OpenIDConnectfeatures-Scopes.
 *
 * @author Jori van Dam
 */
@Singleton
public class SigninConext extends SigninOidc {

    @Inject
    SigninConext() {
        super(new OidcConfig(
                User.AuthMethod.CONEXT,
                Common.getConextDiscoveryUrl(),
                routes.SigninConext.callback().url(),
                Common.getConextClientId(),
                Common.getConextClientSecret(),
                Common.getConextIdTokenSigningAlgorithm(),
                Common.getConextSuccessFeedback()
        ));
    }

}
