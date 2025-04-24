package auth.gui;

import general.common.Common;
import models.common.User;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Sign-in using SURFconext (surfconext.nl) based on OpenID Connect (OIDC).
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
                Common.getConextScope(),
                Common.getConextClientId(),
                Common.getConextClientSecret(),
                Common.getConextIdTokenSigningAlgorithm(),
                Common.getConextSuccessFeedback()
        ));
    }

}
