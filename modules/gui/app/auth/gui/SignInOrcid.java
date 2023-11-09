package auth.gui;

import general.common.Common;
import models.common.User;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Sign-in using ORCID (orcid.org) based on OpenID Connect (OIDC)
 *
 * @author Kristian Lange
 */
@Singleton
public class SignInOrcid extends SignInOidc {

    @Inject
    SignInOrcid() {
        super(new OidcConfig(
                User.AuthMethod.ORCID,
                Common.getOrcidDiscoveryUrl(),
                auth.gui.routes.SignInOrcid.callback().url(),
                Common.getOrcidClientId(),
                Common.getOrcidClientSecret(),
                Common.getOrcidIdTokenSigningAlgorithm(),
                Common.getOrcidSuccessFeedback()
        ));
    }

}
