package auth.gui;

import exceptions.common.JatosException;
import general.common.ApiEnvelope.ErrorCode;
import general.common.Common;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class SigninLdapTest {

    private final SigninLdap signinLdap = new SigninLdap();

    @Test
    public void authenticate_emptyUsernameOrPassword_returnsFalse() {
        assertThat(signinLdap.authenticate("", "pw")).isFalse();
        assertThat(signinLdap.authenticate("bob", "")).isFalse();
        assertThat(signinLdap.authenticate(null, "pw")).isFalse();
        assertThat(signinLdap.authenticate("bob", null)).isFalse();
    }

    @Test
    @SuppressWarnings({ "ResultOfMethodCallIgnored", "unchecked" })
    public void authenticate_withoutAdmin_bindsUserDnAndReturnsTrue() throws NamingException {
        List<Hashtable<String, String>> capturedProps = new ArrayList<>();

        try (MockedStatic<Common> commonMocked = Mockito.mockStatic(Common.class);
             MockedConstruction<InitialDirContext> contexts = Mockito.mockConstruction(InitialDirContext.class,
                     (mock, context) -> capturedProps.add(
                             (Hashtable<String, String>) context.arguments().getFirst()))) {
            commonMocked.when(Common::getLdapBaseDn).thenReturn(List.of("ou=users,dc=example,dc=org"));
            commonMocked.when(Common::getLdapAdminDn).thenReturn("");
            commonMocked.when(Common::getLdapUserAttribute).thenReturn("uid");
            commonMocked.when(Common::getLdapUrl).thenReturn("ldap://ldap.example.org");
            commonMocked.when(Common::getLdapTimeout).thenReturn(1234);

            boolean result = signinLdap.authenticate("bob", "pw");

            assertThat(result).isTrue();
            assertThat(contexts.constructed()).hasSize(1);

            Hashtable<String, String> props = capturedProps.getFirst();
            assertThat(props.get(Context.PROVIDER_URL)).isEqualTo("ldap://ldap.example.org");
            assertThat(props.get(Context.SECURITY_PRINCIPAL)).isEqualTo("uid=bob,ou=users,dc=example,dc=org");
            assertThat(props.get(Context.SECURITY_CREDENTIALS)).isEqualTo("pw");
            assertThat(props.get(Context.SECURITY_AUTHENTICATION)).isEqualTo("simple");
            assertThat(props.get("com.sun.jndi.ldap.read.timeout")).isEqualTo("1234");
            assertThat(props.get("com.sun.jndi.ldap.connect.timeout")).isEqualTo("1234");

            verify(contexts.constructed().getFirst()).close();
        }
    }

    @Test
    @SuppressWarnings({ "ResultOfMethodCallIgnored", "unchecked" })
    public void authenticate_withAdmin_searchesUserThenBindsFoundDn() throws NamingException {
        List<Hashtable<String, String>> capturedProps = new ArrayList<>();

        NamingEnumeration<SearchResult> results = mock(NamingEnumeration.class);
        SearchResult searchResult = mock(SearchResult.class);
        when(results.hasMore()).thenReturn(true);
        when(results.next()).thenReturn(searchResult);
        when(searchResult.getNameInNamespace()).thenReturn("cn=Bob Smith,ou=users,dc=example,dc=org");

        try (MockedStatic<Common> commonMocked = Mockito.mockStatic(Common.class);
             MockedConstruction<InitialDirContext> contexts = Mockito.mockConstruction(InitialDirContext.class,
                     (mock, context) -> {
                         capturedProps.add((Hashtable<String, String>) context.arguments().getFirst());
                         if (context.getCount() == 1) {
                             when(mock.search(eq("ou=users,dc=example,dc=org"), eq("(uid=bob)"),
                                     any(SearchControls.class))).thenReturn(results);
                         }
                     })) {
            commonMocked.when(Common::getLdapBaseDn).thenReturn(List.of("ou=users,dc=example,dc=org"));
            commonMocked.when(Common::getLdapAdminDn).thenReturn("cn=admin,dc=example,dc=org");
            commonMocked.when(Common::getLdapAdminPassword).thenReturn("admin-pw");
            commonMocked.when(Common::getLdapUrl).thenReturn("ldap://ldap.example.org");
            commonMocked.when(Common::getLdapTimeout).thenReturn(1234);

            boolean result = signinLdap.authenticate("bob", "pw");

            assertThat(result).isTrue();
            assertThat(contexts.constructed()).hasSize(2);

            Hashtable<String, String> adminProps = capturedProps.getFirst();
            assertThat(adminProps.get(Context.SECURITY_PRINCIPAL)).isEqualTo("cn=admin,dc=example,dc=org");
            assertThat(adminProps.get(Context.SECURITY_CREDENTIALS)).isEqualTo("admin-pw");

            Hashtable<String, String> userProps = capturedProps.get(1);
            assertThat(userProps.get(Context.SECURITY_PRINCIPAL)).isEqualTo("cn=Bob Smith,ou=users,dc=example,dc=org");
            assertThat(userProps.get(Context.SECURITY_CREDENTIALS)).isEqualTo("pw");

            verify(contexts.constructed().get(0)).close();
            verify(contexts.constructed().get(1)).close();
        }
    }

    @Test
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void authenticate_withAdmin_returnsFalseWhenSearchFindsNoUser() throws NamingException {
        @SuppressWarnings("unchecked")
        NamingEnumeration<SearchResult> results = mock(NamingEnumeration.class);
        when(results.hasMore()).thenReturn(false);

        try (MockedStatic<Common> commonMocked = Mockito.mockStatic(Common.class);
             MockedConstruction<InitialDirContext> contexts = Mockito.mockConstruction(InitialDirContext.class,
                     (mock, context) -> when(mock.search(eq("ou=users,dc=example,dc=org"), eq("(uid=bob)"),
                             any(SearchControls.class))).thenReturn(results))) {
            commonMocked.when(Common::getLdapBaseDn).thenReturn(List.of("ou=users,dc=example,dc=org"));
            commonMocked.when(Common::getLdapAdminDn).thenReturn("cn=admin,dc=example,dc=org");
            commonMocked.when(Common::getLdapAdminPassword).thenReturn("admin-pw");
            commonMocked.when(Common::getLdapUrl).thenReturn("ldap://ldap.example.org");
            commonMocked.when(Common::getLdapTimeout).thenReturn(1234);

            boolean result = signinLdap.authenticate("bob", "pw");

            assertThat(result).isFalse();
            assertThat(contexts.constructed()).hasSize(1);
            verify(contexts.constructed().getFirst()).close();
        }
    }

    @Test
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void authenticate_wrapsNamingExceptionInJatosException() {
        try (MockedStatic<Common> commonMocked = Mockito.mockStatic(Common.class);
             MockedConstruction<InitialDirContext> ignored = Mockito.mockConstruction(InitialDirContext.class,
                     (mock, context) -> when(mock.search(eq("ou=users,dc=example,dc=org"), eq("(uid=bob)"),
                             any(SearchControls.class))).thenThrow(new NamingException("ldap down")))) {
            commonMocked.when(Common::getLdapBaseDn).thenReturn(List.of("ou=users,dc=example,dc=org"));
            commonMocked.when(Common::getLdapAdminDn).thenReturn("cn=admin,dc=example,dc=org");
            commonMocked.when(Common::getLdapAdminPassword).thenReturn("admin-pw");
            commonMocked.when(Common::getLdapUrl).thenReturn("ldap://ldap.example.org");
            commonMocked.when(Common::getLdapTimeout).thenReturn(1234);

            try {
                signinLdap.authenticate("bob", "pw");
                fail("Expected JatosException");
            } catch (JatosException e) {
                assertThat(e.getCause()).isInstanceOf(NamingException.class);
                assertThat(e.getErrorCode()).isEqualTo(ErrorCode.LDAP_ERROR);
                assertThat(e.getCause().getMessage()).isEqualTo("ldap down");
            }
        }
    }
}