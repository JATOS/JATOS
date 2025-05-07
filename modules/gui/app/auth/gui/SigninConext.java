package auth.gui;

import com.nimbusds.openid.connect.sdk.claims.UserInfo;
import exceptions.gui.AuthException;
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
                Common.getConextClientId(),
                Common.getConextClientSecret(),
                Common.getConextScope(),
                Common.getConextUsernameFrom(),
                Common.getConextIdTokenSigningAlgorithm(),
                Common.getConextSuccessFeedback()
        ));
    }

    @Override
    protected String getUsername(UserInfo userInfo, String usernameFrom) throws AuthException {
        if (usernameFrom.equals("eduperson_principal_name")) {
            return userInfo.getStringClaim("eduperson_principal_name");
        }
        return super.getUsername(userInfo, usernameFrom);
    }

}
