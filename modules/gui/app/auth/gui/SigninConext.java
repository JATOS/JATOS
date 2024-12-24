package auth.gui;

import com.nimbusds.oauth2.sdk.Scope;
import general.common.Common;
import models.common.User;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Sign-in using CONEXT (surfconext.nl) based on OpenID Connect (OIDC)
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

    @Override
    public Scope getScope(){
        return new Scope("openid", "profile", "email");
    }

}
