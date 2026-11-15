package models.gui;

import com.fasterxml.jackson.annotation.JsonProperty;
import http.common.HttpUtils;
import models.common.StudySource;
import play.data.validation.Constraints;
import play.data.validation.Constraints.Validatable;
import play.data.validation.ValidationError;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY;
import static com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY;

/**
 * Input and output DTO for source settings. Secrets are write-only; source settings are never part of study exports.
 */
@Constraints.Validate
public class StudySourceProperties implements Validatable<List<ValidationError>> {

    /**
     * Selected source kind; NONE deletes the study's source settings incl. the study-specific Git credentials.
     */
    private StudySource.Type type;

    /**
     * HTTPS repository URL without embedded credentials.
     */
    private String gitUrl;

    /**
     * Branch or tag name; blank selects the repository default branch.
     */
    private String gitRef;

    /**
     * Absolute source folder path on the JATOS server.
     */
    private String localFolderPath;

    /**
     * Use Git without credentials, overriding study, owner, and global credentials.
     */
    private boolean anonymous;

    /**
     * Delete study-specific Git credentials; cannot be combined with a nonempty secret.
     */
    @JsonProperty(access = WRITE_ONLY)
    private boolean removeCredentials;

    /**
     * Stored Git username; changing it requires a new password/token.
     */
    private String username;

    /**
     * Plaintext password or token supplied for encryption; never serialized in responses.
     */
    @JsonProperty(access = WRITE_ONLY)
    private String secret;

    /**
     * Whether study-specific Git credentials are stored; supplied by the server.
     */
    @JsonProperty(access = READ_ONLY)
    private boolean credentialsConfigured;

    /**
     * Whether this installation allows local-folder sources; supplied by the server.
     */
    @JsonProperty(access = READ_ONLY)
    private boolean localFolderAllowed;

    public boolean isCredentialsConfigured() {
        return credentialsConfigured;
    }

    public void setCredentialsConfigured(boolean value) {
        credentialsConfigured = value;
    }

    public boolean isLocalFolderAllowed() {
        return localFolderAllowed;
    }

    public void setLocalFolderAllowed(boolean value) {
        localFolderAllowed = value;
    }

    @Override
    public List<ValidationError> validate() {
        List<ValidationError> errors = new ArrayList<>();

        if (type == null) errors.add(new ValidationError("type", "Specify a source type: NONE, GIT, or LOCAL_FOLDER."));

        if (type == StudySource.Type.GIT) {
            try {
                if (gitUrl == null || gitUrl.length() > 2048)
                    throw new IllegalArgumentException("Use an HTTPS Git URL of at most 2048 characters.");
                HttpUtils.getGitOriginFromRepositoryUrl(gitUrl);
            } catch (IllegalArgumentException e) {errors.add(new ValidationError("gitUrl", e.getMessage()));}

            if (!validGitRef(gitRef))
                errors.add(new ValidationError("gitRef", "Use a branch or tag name, or leave empty for the default branch. Commit IDs are not supported."));

            if (removeCredentials && secret != null && !secret.isEmpty())
                errors.add(new ValidationError("removeCredentials", "Cannot remove credentials and supply a new secret together."));

            if ((secret != null && !secret.isEmpty()) || (!removeCredentials && username != null && !username.isEmpty())) {
                if (username == null || username.isBlank() || username.length() > 255
                        || username.indexOf(':') >= 0 || username.chars().anyMatch(Character::isISOControl)) {
                    errors.add(new ValidationError("username", "Use a Git username of at most 255 characters without colons or control characters."));
                }
                if (secret != null && secret.length() > 16384)
                    errors.add(new ValidationError("secret", "Use a password or token of at most 16384 characters."));
            }
        }

        if (type == StudySource.Type.LOCAL_FOLDER) {
            try {
                if (localFolderPath == null || localFolderPath.isBlank() || localFolderPath.length() > 4096
                        || !Path.of(localFolderPath).isAbsolute()) throw new IllegalArgumentException();
            } catch (IllegalArgumentException e) {
                errors.add(new ValidationError("localFolderPath", "Use an absolute folder path on the JATOS host, up to 4096 characters."));
            }
        }

        return errors.isEmpty() ? null : errors;
    }

    private static boolean validGitRef(String value) {
        if (value == null || value.isBlank()) return true;
        String ref = value.trim();
        if (ref.length() > 255 || ref.equals("@") || ref.startsWith("-") || ref.startsWith("/")
                || ref.endsWith("/") || ref.endsWith(".") || ref.contains("..") || ref.contains("@{")
                || ref.contains("//") || ref.matches("(?i)[0-9a-f]{40}|[0-9a-f]{64}")
                || ref.chars().anyMatch(c -> c <= 32 || c == 127 || "~^:?*[\\".indexOf(c) >= 0)) return false;
        for (String part : ref.split("/")) {
            if (part.startsWith(".") || part.endsWith(".lock")) return false;
        }
        return true;
    }

    public StudySource.Type getType() {
        return type;
    }

    public void setType(StudySource.Type value) {
        type = value;
    }

    public String getGitUrl() {
        return gitUrl;
    }

    public void setGitUrl(String value) {
        gitUrl = value;
    }

    public String getGitRef() {
        return gitRef;
    }

    public void setGitRef(String value) {
        gitRef = value;
    }

    public String getLocalFolderPath() {
        return localFolderPath;
    }

    public void setLocalFolderPath(String value) {
        localFolderPath = value;
    }

    public boolean isAnonymous() {
        return anonymous;
    }

    public void setAnonymous(boolean value) {
        anonymous = value;
    }

    public boolean isRemoveCredentials() {
        return removeCredentials;
    }

    public void setRemoveCredentials(boolean value) {
        removeCredentials = value;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String value) {
        username = value;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String value) {
        secret = value;
    }
}
