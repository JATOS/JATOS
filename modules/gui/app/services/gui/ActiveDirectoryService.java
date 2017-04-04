package services.gui;

import java.util.Hashtable;
import java.util.concurrent.CompletableFuture;

import javax.naming.AuthenticationException;
import javax.naming.CommunicationException;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

public class ActiveDirectoryService {

	public static final String ldapURL = "ldap.forumsys.com ";// Play.application().configuration()
	// .getString("ActiveDirectory.url");
	public static final String domainName = "dc=example,dc=com";// Play.application().configuration()
	// .getString("ActoveDirectory.DomainName");
	public static final int timeout = 30;// Play.application().configuration()
	// .getInt("ActoveDirectory.timeout");

	public static CompletableFuture<Boolean> authenticate(String username,
			String password) throws AuthenticationException,
			CommunicationException, NamingException {

		Hashtable<String, String> env = new Hashtable<String, String>();

		env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
		env.put(Context.PROVIDER_URL, "ldap://ldap.forumsys.com:389");

		// Authenticate as S. User and password “mysecret”
		env.put(Context.SECURITY_AUTHENTICATION, "simple");
		env.put(Context.SECURITY_PRINCIPAL, "uid="+ username +",dc=example,dc=com");
		env.put(Context.SECURITY_CREDENTIALS, password);
		
		env.put("com.sun.jndi.ldap.connect.timeout", "" + (timeout * 1000));

		DirContext authContext = null;
		authContext = new InitialDirContext(env);
		return CompletableFuture.completedFuture(Boolean.TRUE);
	}

}
