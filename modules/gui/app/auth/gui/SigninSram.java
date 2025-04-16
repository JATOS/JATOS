package auth.gui;

import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;
import exceptions.gui.BadRequestException;
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

    private static final String EMAIL = "email";

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
    protected Scope getScope(){
        return new Scope("openid", "profile", "email");
    }

    // SRAM's subject claim value is quite ugly, so we prefer to use the "email" claim instead. For SRAM, it is also
    // necessary that users from different organisations are distinguishable, so using the email address works well for
    // this
    @Override
    protected String getUsername(UserInfo userInfo) throws BadRequestException {
        String email = userInfo.getStringClaim(EMAIL);
        if (email == null) throw new BadRequestException("\"email\" claim is not specified or could not be casted");
        return email;
    }

}
