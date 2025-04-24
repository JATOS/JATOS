package auth.gui;

import general.common.Common;
import models.common.User;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @author Kristian Lange
 */
@Singleton
public class SigninBasicOidc extends SigninOidc {

    @Inject
    SigninBasicOidc() {
        super(new OidcConfig(
                User.AuthMethod.OIDC,
                Common.getOidcDiscoveryUrl(),
                auth.gui.routes.SigninBasicOidc.callback().url(),
                Common.getOidcScope(),
                Common.getOidcClientId(),
                Common.getOidcClientSecret(),
                Common.getOidcIdTokenSigningAlgorithm(),
                Common.getOidcSuccessFeedback(),
                Common.oidcUseEmailAsUsername()
        ));
    }

}
