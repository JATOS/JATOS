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
                User.AuthMethod.ORCID,
                Common.getOrcidDiscoveryUrl(),
                auth.gui.routes.SigninOrcid.callback().url(),
                Common.getOrcidClientId(),
                Common.getOrcidClientSecret(),
                Common.getOrcidIdTokenSigningAlgorithm(),
                Common.getOrcidSuccessFeedback()
        ));
    }

    @Inject
    @Override
    public String getScope(){
        return "openid profile email";
    }

}
