package auth.gui;

import com.nimbusds.oauth2.sdk.Scope;
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
                Common.getSramClientId(),
                Common.getSramClientSecret(),
                Common.getSramIdTokenSigningAlgorithm(),
                Common.getSramSuccessFeedback()
        ));
    }

    @Override
    public Scope getScope(){
        return new Scope("openid", "profile", "email");
    }

}
