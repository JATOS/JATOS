package auth.gui;

import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;
import exceptions.gui.BadRequestException;
import general.common.Common;
import models.common.User;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

/**
 * Sign-in using CONEXT (surfconext.nl) based on OpenID Connect (OIDC)
 *
 * @author Jori van Dam
 */
@Singleton
public class SigninConext extends SigninOidc {

    private static final String UIDS = "uids";

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

    // SURFconext ignores scopes other than openid. See: https://servicedesk.surf.nl/wiki/spaces/IAM/pages/128909987/OpenID+Connect+features#OpenIDConnectfeatures-Scopes
    @Override
    protected Scope getScope(){
        return new Scope("openid");
    }

    // SURFconext does not use the subject claim as a unique identifier for users
    @Override
    protected String getUsername(UserInfo userInfo) throws BadRequestException {
        List<String> uids = userInfo.getStringListClaim(UIDS);
        if (uids == null) throw new BadRequestException("\"uids\" claim is not specified or could not be parsed");
        if (uids.isEmpty()) throw new BadRequestException("\"uids\" claim does not have any value");
        if (uids.size() > 1) throw new BadRequestException("Multiple values found for claim \"uids\", while one was expected");
        return uids.get(0);
    }

}
