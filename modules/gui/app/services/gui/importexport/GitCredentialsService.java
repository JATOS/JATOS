package services.gui.importexport;

import daos.common.UserDao;
import daos.common.StudyDao;
import exceptions.common.BadRequestException;
import exceptions.common.FieldValidationException;
import exceptions.common.ForbiddenException;
import exceptions.common.NotFoundException;
import general.common.JpaTransactions;
import http.common.Http.Context;
import http.common.HttpUtils;
import models.common.GitCredentials;
import models.common.Study;
import models.common.StudySource;
import models.common.User;
import models.gui.GitCredentialsProperties;
import services.gui.AuthorizationService;
import utils.common.GitCredentialEncryption;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Objects;
import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;

import static auth.gui.AuthAction.SIGNEDIN_USER;

/**
 * Stores and manages Git credentials. Users can manage their own credentials; study-source credentials require the
 * study owner or an enabled superuser. Returns credential metadata but never stored secrets. Managed owner
 * relationships persist credentials through cascading and delete them through orphan removal.
 */
@Singleton
public class GitCredentialsService {

    /**
     * Are the credentials configured? Use it if authentication details shouldn't be published.
     */
    public record GitCredentialsConfigStatus(boolean configured, String origin, String username) {}

    private final JpaTransactions transactions;
    private final UserDao userDao;
    private final StudyDao studyDao;
    private final AuthorizationService authorization;
    private final GitCredentialEncryption encryption;

    @Inject
    public GitCredentialsService(JpaTransactions transactions,
                                 UserDao userDao,
                                 StudyDao studyDao,
                                 AuthorizationService authorization,
                                 GitCredentialEncryption encryption) {
        this.transactions = transactions;
        this.userDao = userDao;
        this.studyDao = studyDao;
        this.authorization = authorization;
        this.encryption = encryption;
    }

    /**
     * Returns the signed-in user's credential configuration status.
     */
    public GitCredentialsConfigStatus getUserGitCredentialsConfigStatus() {
        return transactions.withTransaction(em -> {
            return status(currentUser().getGitCredentials());
        });
    }

    /**
     * Applies a partial credential update, including explicit removal and field-error paths.
     */
    public void updateGitCredentialsForUser(GitCredentialsProperties props) {
        transactions.withTransaction(em -> {
            if (props.isRemoveCredentials()) {
                if (props.getSecret() != null && !props.getSecret().isEmpty())
                    throw new FieldValidationException("gitCredentials.removeCredentials", "Cannot remove and replace Git credentials at the same time.");
                removeGitCredentialsFromUser();
            } else {
                var stored = getUserGitCredentialsConfigStatus();
                try {
                    saveGitCredentialsForUser(
                            props.getUsername() == null ? stored.username() : props.getUsername(),
                            props.getOrigin() == null ? stored.origin() : props.getOrigin(), props.getSecret());
                } catch (FieldValidationException e) {
                    Map<String, List<String>> fields = new LinkedHashMap<>();
                    e.getFieldErrors().forEach((field, messages) -> fields.put("gitCredentials." + field, messages));
                    throw new FieldValidationException(e.getMessage(), fields);
                }
            }
        });
    }

    /**
     * Update the signed-in user's own credentials; an empty secret preserves an existing credential.
     */
    public void saveGitCredentialsForUser(String username, String origin, String secret) {
        transactions.withTransaction(em -> {
            User user = currentUser();
            GitCredentials credentials = user.getGitCredentials();
            if (credentials == null) credentials = new GitCredentials(user);
            update(credentials, username, origin, secret);
            user.setGitCredentials(credentials);
        });
    }

    /**
     * Removes only the signed-in user's Git credentials.
     */
    public void removeGitCredentialsFromUser() {
        transactions.withTransaction(em -> {
            currentUser().setGitCredentials(null);
        });
    }

    public GitCredentialsConfigStatus getSourceGitCredentialsConfigStatus(long studyId) {
        return transactions.withTransaction(em -> {
            Study study = ownedStudy(studyId);
            StudySource source = study.getStudySource();
            return status(source == null ? null : source.getGitCredentials());
        });
    }

    public void saveGitCredentialsForSource(long studyId, String username, String origin, String secret) {
        transactions.withTransaction(em -> {
            Study study = ownedStudy(studyId);
            StudySource source = study.getStudySource();
            if (source == null || source.getType() != StudySource.Type.GIT) {
                throw new BadRequestException("Configure a Git source before saving its credentials.");
            }
            String normalized = origin(origin);
            try {
                if (!normalized.equals(HttpUtils.getGitOriginFromRepositoryUrl(source.getGitUrl()))) {
                    throw new BadRequestException("The credential origin must match the Git repository origin.");
                }
            } catch (IllegalArgumentException e) {throw new BadRequestException(e.getMessage());}
            GitCredentials credentials = source.getGitCredentials();
            if (credentials == null) credentials = new GitCredentials(source);
            update(credentials, username, normalized, secret);
            source.setGitCredentials(credentials);
        });
    }

    public void removeGitCredentialsFromSource(long studyId) {
        transactions.withTransaction(em -> {
            StudySource source = ownedStudy(studyId).getStudySource();
            if (source != null) source.setGitCredentials(null);
        });
    }

    /**
     * Re-fetch the current user under a user lock before the study lock, following the existing deletion lock order.
     */
    private User currentUser() {
        User signedinUser = Context.current().args().getOrElse(SIGNEDIN_USER, null);
        if (signedinUser == null) throw new ForbiddenException("Authentication is required to manage Git credentials.");
        User user = userDao.findByUsernameWithGitCredentialsAndLock(signedinUser.getUsername());
        if (user == null) throw new ForbiddenException("This user account no longer exists.");
        if (!user.isActive()) throw new ForbiddenException("This user account is inactive.");
        return user;
    }

    private Study ownedStudy(long studyId) {
        User currentUser = currentUser();
        Study study = studyDao.findByIdWithSourceAndLock(studyId);
        if (study == null) throw new NotFoundException("Study doesn't exist.");
        authorization.checkLockedStudyOwner(study, currentUser, false);
        return study;
    }

    private GitCredentialsConfigStatus status(GitCredentials credentials) {
        return new GitCredentialsConfigStatus(
                credentials != null,
                credentials == null ? null : credentials.getOrigin(),
                credentials == null ? null : credentials.getUsername());
    }

    private void update(GitCredentials credentials, String username, String origin, String secret) {
        String normalized = validateUpdate(credentials, username, origin, secret);
        // Validation guarantees that keeping the secret also keeps its username and origin.
        if (secret == null || secret.isEmpty()) return;
        credentials.setUsername(username);
        credentials.setOrigin(normalized);
        credentials.setEncryptedSecret(encryption.encrypt(secret, encryptionContext(credentials)));
    }

    /**
     * Validates a credential update without changing the entity and returns the normalized origin.
     */
    private String validateUpdate(GitCredentials credentials, String username, String origin, String secret) {
        String normalized = origin(origin);
        if (username == null || username.isBlank() || username.length() > 255
                || username.indexOf(':') >= 0 || username.chars().anyMatch(Character::isISOControl)) {
            throw new FieldValidationException("username", "Use a Git username of at most 255 characters without colons or control characters.");
        }
        if (secret != null && secret.length() > 16384)
            throw new FieldValidationException("secret", "The Git password or token is too long.");
        if (secret == null || secret.isEmpty()) {
            if (credentials.getEncryptedSecret() == null)
                throw new FieldValidationException("secret", "Provide a Git password or token.");
            if (!Objects.equals(username, credentials.getUsername())) {
                throw new FieldValidationException("secret", "Provide a new Git password or token when changing the username.");
            }
            if (!Objects.equals(normalized, credentials.getOrigin())) {
                throw new FieldValidationException("secret", "Provide a new Git password or token when changing the origin.");
            }
        }
        return normalized;
    }

    /**
     * Binds encrypted credentials to their owner, origin, and username during encryption and decryption.
     */
    public static String encryptionContext(GitCredentials credentials) {
        String owner = credentials.getUser() != null
                ? "user:" + credentials.getUser().getUsername()
                : "study:" + credentials.getStudySource().getStudy().getId();
        return owner + "\0" + credentials.getOrigin() + "\0" + credentials.getUsername();
    }

    private String origin(String value) {
        try {return HttpUtils.normalizeGitOrigin(value);} catch (IllegalArgumentException e) {
            throw new FieldValidationException("origin", e.getMessage());
        }
    }
}
