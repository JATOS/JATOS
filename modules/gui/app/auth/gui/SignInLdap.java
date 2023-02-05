package auth.gui;

import com.google.common.base.Strings;
import general.common.Common;

import javax.inject.Singleton;
import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import java.util.Hashtable;

/**
 * @author Kristian Lange
 */
@Singleton
public class SignInLdap {

    /**
     * Authenticates the given user via an external LDAP server. It throws an {@link NamingException} if the LDAP server can't
     * be reached or the LDAP URL or Base DN is wrong. It allows multiple base DNs and tries to authenticate against
     * each of them one after another. If an admin user is specified it tries to search for the user and then
     * authenticates- if not it tries to authenticate right away. The username is used as the uid in LDAP.
     * https://stackoverflow.com/a/24752175/1278769
     */
    public boolean authenticate(String username, String password) throws NamingException {
        if (Strings.isNullOrEmpty(username) || Strings.isNullOrEmpty(password)) return false;

        // Try authentication against each base DN
        for (String baseDn : Common.getLdapBaseDn()) {
            if (Strings.isNullOrEmpty(Common.getLdapAdminDn())) {
                String userDn = "uid=" + username + "," + baseDn;
                if (authenticateUser(userDn, password)) return true;
            } else {
                String userDn = searchUser(username, baseDn);
                if (userDn != null && authenticateUser(userDn, password)) return true;
            }
        }
        return false;
    }

    private boolean authenticateUser(String userDn, String password) throws NamingException {
        InitialDirContext userContext = null;
        try {
            userContext = bind(userDn, password);
            return true;
        } catch (AuthenticationException e) {
            return false;
        } finally {
            if (userContext != null) userContext.close();
        }
    }

    private InitialDirContext bind(String userDn, String password) throws NamingException {
        Hashtable<String, String> props = new Hashtable<>();
        props.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        props.put(Context.PROVIDER_URL, Common.getLdapUrl());
        props.put(Context.SECURITY_PRINCIPAL, userDn);
        props.put(Context.SECURITY_AUTHENTICATION, "simple");
        props.put(Context.SECURITY_CREDENTIALS, password);
        props.put("com.sun.jndi.ldap.read.timeout", String.valueOf(Common.getLdapTimeout()));
        props.put("com.sun.jndi.ldap.connect.timeout", String.valueOf(Common.getLdapTimeout()));
        return new InitialDirContext(props);
    }

    /**
     * Uses an LDAP admin to search for the uid of the user to be authenticated
     */
    private String searchUser(String username, String baseDn) throws NamingException {
        InitialDirContext adminContext = bind(Common.getLdapAdminDn(), Common.getLdapAdminPassword());
        String filter = "(uid=" + username + ")";
        SearchControls ctrls = new SearchControls();
        ctrls.setReturningAttributes(new String[] { "cn" });
        ctrls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        NamingEnumeration<SearchResult> results = adminContext.search(baseDn, filter, ctrls);
        adminContext.close();
        return results.hasMore() ? results.next().getNameInNamespace() : null;
    }
}
