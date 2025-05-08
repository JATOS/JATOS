package auth.gui;

import com.nimbusds.openid.connect.sdk.claims.UserInfo;
import exceptions.gui.AuthException;
import general.common.Common;
import models.common.User;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Sign-in using SRAM (sram.surf.nl) based on OpenID Connect (OIDC).
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

    @Override
    protected String getUsername(UserInfo userInfo, String usernameFrom) throws AuthException {
        if (usernameFrom.equals("eduperson_principal_name")) {
            return userInfo.getStringListClaim("voperson_external_id").stream().findFirst()
                    .orElseThrow(() -> new AuthException("OIDC error - could not get username value from OIDC claims"));
        }
        return super.getUsername(userInfo, usernameFrom);
    }

}
