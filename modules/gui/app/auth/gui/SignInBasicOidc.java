package auth.gui;

import general.common.Common;
import models.common.User;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @author Kristian Lange
 */
@Singleton
public class SignInBasicOidc extends SignInOidc {

    @Inject
    SignInBasicOidc() {
        super(new OidcConfig(
                User.AuthMethod.OIDC,
                Common.getOidcDiscoveryUrl(),
                auth.gui.routes.SignInBasicOidc.callback().url(),
                Common.getOidcClientId(),
                Common.getOidcClientSecret(),
                Common.getOidcIdTokenSigningAlgorithm(),
                Common.getOidcSuccessFeedback()
        ));
    }

}
