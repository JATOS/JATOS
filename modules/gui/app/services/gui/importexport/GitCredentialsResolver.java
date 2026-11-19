package services.gui.importexport;

import com.google.common.base.Strings;
import exceptions.common.BadRequestException;
import general.common.Common;
import http.common.HttpUtils;
import models.common.GitCredentials;
import models.common.Study;
import models.common.User;
import models.gui.StudySourceProperties;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import utils.common.GitCredentialEncryption;

import javax.inject.Inject;
import javax.inject.Singleton;

import static general.common.ApiEnvelope.ErrorCode.CONFIG_ERROR;

/**
 * Selects one credential before accessing Git. Callers authorize access and supply managed owners in a transaction. A
 * failed authentication never falls back to another credential. Only the configured HTTPS origin receives it.
 * Credentials can be stored in the database for a study source or a user, with encrypted secrets, or supplied globally
 * through jatos.conf. Selection order is study credentials, matching owner credentials, matching global credentials,
 * then anonymous access. Explicit anonymous access overrides all configured credentials.
 */
@Singleton
public class GitCredentialsResolver {

    private final GitCredentialEncryption encryption;

    @Inject
    public GitCredentialsResolver(GitCredentialEncryption encryption) {
        this.encryption = encryption;
    }

    /**
     * Selects credentials for a new study from validated study source settings and its future owner.
     */
    public ResolvedCredentials forStudyCreation(StudySourceProperties props, User owner) {
        String origin = HttpUtils.getGitOriginFromRepositoryUrl(props.getGitUrl());
        if (props.isAnonymous()) {
            return resolveAnonymousCredentials(origin);
        }
        if (!Strings.isNullOrEmpty(props.getSecret())) {
            return new ResolvedCredentials(origin, "study", props.getUsername(), props.getSecret());
        }
        return resolveOwnerOrGlobalCredentials(origin, owner);
    }

    /**
     * Selects credentials for an existing study, honoring its anonymous override and stored credentials.
     */
    public ResolvedCredentials forExistingStudy(Study study) {
        var studySource = study.getStudySource();
        String origin = HttpUtils.getGitOriginFromRepositoryUrl(studySource.getGitUrl());
        if (studySource.isAnonymous()) {
            return resolveAnonymousCredentials(origin);
        }
        if (studySource.getGitCredentials() != null) {
            return resolveStoredCredentials(origin, "study", studySource.getGitCredentials());
        }
        return resolveOwnerOrGlobalCredentials(origin, study.getOwner());
    }

    /**
     * Uses matching owner credentials, otherwise considers global credentials.
     */
    private ResolvedCredentials resolveOwnerOrGlobalCredentials(String origin, User owner) {
        GitCredentials credential = owner.getGitCredentials();
        if (credential != null && origin.equals(credential.getOrigin())) {
            return resolveStoredCredentials(origin, "owner", credential);
        }
        return resolveGlobalOrAnonymousCredentials(origin);
    }

    /**
     * Validates global settings and uses their credentials for a matching origin, otherwise returns anonymous access.
     */
    private ResolvedCredentials resolveGlobalOrAnonymousCredentials(String origin) {
        String configuredOrigin = Common.getStudySourcesGitCredentialOrigin();
        String username = Common.getStudySourcesGitCredentialUsername();
        String secret = Common.getStudySourcesGitCredentialSecret();
        if (Strings.isNullOrEmpty(configuredOrigin) && Strings.isNullOrEmpty(username) && Strings.isNullOrEmpty(secret))
            return resolveAnonymousCredentials(origin);
        String normalizedOrigin;
        try {
            normalizedOrigin = HttpUtils.normalizeGitOrigin(configuredOrigin);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Global Git credentials require a valid HTTPS origin without a repository path.", CONFIG_ERROR);
        }
        if (username == null || username.isBlank() || username.length() > 255
                || username.indexOf(':') >= 0 || username.chars().anyMatch(Character::isISOControl)
                || Strings.isNullOrEmpty(secret))
            throw new BadRequestException("Global Git credentials require a valid username and password/token.", CONFIG_ERROR);
        if (origin.equals(normalizedOrigin)) return new ResolvedCredentials(origin, "global", username, secret);
        return resolveAnonymousCredentials(origin);
    }

    /**
     * Checks the stored credential origin and decrypts its secret; failures never trigger a fallback.
     */
    private ResolvedCredentials resolveStoredCredentials(String origin, String source, GitCredentials credential) {
        if (!origin.equals(credential.getOrigin()))
            throw new BadRequestException("The stored Git credential origin does not match the repository. Replace or remove it.");
        try {
            String decryptedSecret = encryption.decrypt(credential.getEncryptedSecret(), GitCredentialsService.encryptionContext(credential));
            return new ResolvedCredentials(origin, source, credential.getUsername(), decryptedSecret);
        } catch (IllegalStateException e) {
            throw new BadRequestException("Stored Git credentials cannot be decrypted. Enter the password or token again.");
        }
    }

    /**
     * Creates a provider that supplies no credentials.
     */
    private ResolvedCredentials resolveAnonymousCredentials(String origin) {
        return new ResolvedCredentials(origin, "anonymous", null, null);
    }

    /**
     * Noninteractive, origin-bound provider. Deliberately not a DTO; never return it in an API response.
     */
    public static final class ResolvedCredentials extends CredentialsProvider implements AutoCloseable {

        private final String origin;
        private final String source;
        private final UsernamePasswordCredentialsProvider delegate;

        /**
         * Binds credentials to one origin or creates anonymous access when the secret is absent.
         */
        private ResolvedCredentials(String origin, String source, String username, String secret) {
            this.origin = origin;
            this.source = source;
            delegate = secret == null ? null : new UsernamePasswordCredentialsProvider(username, secret);
        }

        /**
         * Returns the credential source label for feedback, without revealing credentials.
         */
        public String source() {
            return source;
        }

        /**
         * Prevents JGit from prompting for credentials during server requests.
         */
        @Override
        public boolean isInteractive() {
            return false;
        }

        /**
         * Reports whether the selected provider can supply the requested credential items.
         */
        @Override
        public boolean supports(CredentialItem... items) {
            return delegate != null && delegate.supports(items);
        }

        /**
         * Supplies credentials only for HTTPS requests to the configured origin.
         */
        @Override
        public boolean get(URIish uri, CredentialItem... items) {
            if (delegate == null) return false;
            try {
                if (!origin.equals(HttpUtils.getGitOriginFromRepositoryUrl(uri.toString()))) return false;
            } catch (IllegalArgumentException e) {return false;}
            return delegate.get(uri, items);
        }

        /**
         * Clears the password held by the delegated credential provider.
         */
        @Override
        public void close() {
            if (delegate != null) delegate.clear();
        }
    }
}
