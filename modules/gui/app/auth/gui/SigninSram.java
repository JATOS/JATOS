package auth.gui;

import general.common.Common;
import models.common.User;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Sign-in using SRAM (sram.surf.nl) based on OpenID Connect (OIDC)
 * <p>
 * Note that the email OIDC claim is used as the username. This allows for users to be uniquely identified, even across
 * institutions. Email addresses are also human-readable, making them quite suitable for certain functionalities of the
 * JATOS application, such as adding users to studies. For applications using OIDC it is generally recommended to use
 * a unique persistent identifier (typically supplied through the sub claim), because a user's email address may change,
 * but with such a value readability is lost.
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
                Common.getSramClientId(),
                Common.getSramClientSecret(),
                Common.getSramScope(),
                Common.getSramUsernameFrom(),
                Common.getSramIdTokenSigningAlgorithm(),
                Common.getSramSuccessFeedback()
        ));
    }

}
