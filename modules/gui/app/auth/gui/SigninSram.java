package auth.gui;

import general.common.Common;
import models.common.User;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Sign-in using SRAM (sram.surf.nl) based on OpenID Connect (OIDC)
 *
 * @author Jori van Dam
 */
@Singleton
public class SigninSram extends SigninOidc {

    @Inject
    SigninSram() {
        super(new OidcConfig(
                User.AuthMethod.SRAM,
                Common.getSramDiscoveryUrl(),
                auth.gui.routes.SigninSram.callback().url(),
                Common.getSramScope(),
                Common.getSramClientId(),
                Common.getSramClientSecret(),
                Common.getSramIdTokenSigningAlgorithm(),
                Common.getSramSuccessFeedback(),
                Common.sramUseEmailAsUsername()
        ));
    }

}
