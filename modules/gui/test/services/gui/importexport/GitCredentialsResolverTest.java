package services.gui.importexport;

import exceptions.common.BadRequestException;
import general.common.Common;
import models.common.GitCredentials;
import models.common.Study;
import models.common.StudySource;
import models.common.User;
import models.gui.StudySourceProperties;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.URIish;
import org.junit.Test;
import utils.common.GitCredentialEncryption;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GitCredentialsResolverTest {

    private final GitCredentialEncryption encryption = mock(GitCredentialEncryption.class);
    private final GitCredentialsResolver resolver = new GitCredentialsResolver(encryption);

    private StudySourceProperties props() {
        StudySourceProperties props = new StudySourceProperties();
        props.setType(StudySource.Type.GIT);
        props.setGitUrl("https://example.org/repo.git");
        return props;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    public void selectsStudyThenMatchingOwnerThenGlobalThenAnonymous() throws Exception {
        User owner = new User("owner", "Owner", "owner@example.org");
        Study study = new Study();
        study.setId(42L);
        study.setOwner(owner);
        StudySource source = new StudySource(study);
        source.setType(StudySource.Type.GIT);
        source.setGitUrl(props().getGitUrl());
        study.setStudySource(source);
        GitCredentials own = new GitCredentials(owner);
        own.setOrigin("https://example.org");
        own.setUsername("owner-git");
        own.setEncryptedSecret("owner-cipher");
        owner.setGitCredentials(own);
        GitCredentials specific = new GitCredentials(source);
        specific.setOrigin("https://example.org");
        specific.setUsername("study-git");
        specific.setEncryptedSecret("study-cipher");
        source.setGitCredentials(specific);
        when(encryption.decrypt(eq("owner-cipher"), anyString())).thenReturn("owner-token");
        when(encryption.decrypt(eq("study-cipher"), anyString())).thenReturn("study-token");
        try (var common = mockStatic(Common.class)) {
            common.when(Common::getStudySourcesGitCredentialOrigin).thenReturn("https://example.org");
            common.when(Common::getStudySourcesGitCredentialUsername).thenReturn("global-git");
            common.when(Common::getStudySourcesGitCredentialSecret).thenReturn("global-token");
            assertCredential(study, "study", "study-git", "study-token");
            source.setGitCredentials(null);
            assertCredential(study, "owner", "owner-git", "owner-token");
            own.setOrigin("https://other.example.org");
            assertCredential(study, "global", "global-git", "global-token");
            common.when(Common::getStudySourcesGitCredentialOrigin).thenReturn("https://other.example.org");
            try (var access = resolver.forExistingStudy(study)) {assertEquals("anonymous", access.source());}
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    public void validatesGlobalCredentialsOnlyWhenNeededAndNormalizesOrigin() {
        User owner = new User("owner", "Owner", "owner@example.org");
        try (var common = mockStatic(Common.class)) {
            common.when(Common::getStudySourcesGitCredentialOrigin).thenReturn("https://EXAMPLE.org:443");
            common.when(Common::getStudySourcesGitCredentialUsername).thenReturn("user");
            common.when(Common::getStudySourcesGitCredentialSecret).thenReturn("token");
            try (var access = resolver.forStudyCreation(props(), owner)) {assertEquals("global", access.source());}
            common.when(Common::getStudySourcesGitCredentialUsername).thenReturn("invalid:user");
            assertThrows(BadRequestException.class, () -> resolver.forStudyCreation(props(), owner));
            common.when(Common::getStudySourcesGitCredentialOrigin).thenReturn("http://example.org");
            assertThrows(BadRequestException.class, () -> resolver.forStudyCreation(props(), owner));

            var props = props();
            props.setAnonymous(true);
            try (var access = resolver.forStudyCreation(props, owner)) {assertEquals("anonymous", access.source());}
            props.setAnonymous(false);
            props.setUsername("study-user");
            props.setSecret("study-token");
            try (var access = resolver.forStudyCreation(props, owner)) {assertEquals("study", access.source());}
            GitCredentials credential = new GitCredentials(owner);
            credential.setOrigin("https://example.org");
            credential.setUsername("owner-user");
            credential.setEncryptedSecret("cipher");
            owner.setGitCredentials(credential);
            when(encryption.decrypt(eq("cipher"), anyString())).thenReturn("owner-token");
            try (var access = resolver.forStudyCreation(props(), owner)) {assertEquals("owner", access.source());}
        }
    }

    private void assertCredential(Study study, String expectedSource, String username, String token) throws Exception {
        try (var access = resolver.forExistingStudy(study)) {
            assertEquals(expectedSource, access.source());
            var name = new CredentialItem.Username();
            var password = new CredentialItem.Password();
            assertTrue(access.get(new URIish("https://example.org/repo.git"), name, password));
            assertEquals(username, name.getValue());
            assertEquals(token, new String(password.getValue()));
            password.clear();
        }
    }

    @Test
    public void explicitAnonymousBypassesStoredCredentialsAndOriginBindingIncludesPort() throws Exception {
        User owner = new User("owner", "Owner", "owner@example.org");
        var props = props();
        props.setUsername("user");
        props.setSecret("token");
        try (var access = resolver.forStudyCreation(props, owner)) {
            assertEquals("study", access.source());
            for (String url : new String[]{"https://other.example.org/repo", "https://example.org:8443/repo", "http://example.org/repo"})
                assertFalse(access.get(new URIish(url), new CredentialItem.Username(), new CredentialItem.Password()));
            assertTrue(access.get(new URIish("https://EXAMPLE.org:443/repo"), new CredentialItem.Username(), new CredentialItem.Password()));
        }
        props.setAnonymous(true);
        try (var access = resolver.forStudyCreation(props, owner)) {
            assertEquals("anonymous", access.source());
            assertFalse(access.get(new URIish(props.getGitUrl()), new CredentialItem.Password()));
        }
        verifyNoInteractions(encryption);
    }

    @Test
    public void decryptionFailureDoesNotFallBack() {
        User owner = new User("owner", "Owner", "owner@example.org");
        GitCredentials credential = new GitCredentials(owner);
        credential.setOrigin("https://example.org");
        credential.setUsername("user");
        credential.setEncryptedSecret("broken");
        owner.setGitCredentials(credential);
        when(encryption.decrypt(anyString(), anyString())).thenThrow(new IllegalStateException("secret details"));
        BadRequestException error = assertThrows(BadRequestException.class, () -> resolver.forStudyCreation(props(), owner));
        assertTrue(error.getMessage().contains("again"));
        assertFalse(error.getMessage().contains("secret details"));
    }
}
