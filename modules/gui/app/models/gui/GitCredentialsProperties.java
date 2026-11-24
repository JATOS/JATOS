package models.gui;

import com.fasterxml.jackson.annotation.JsonProperty;

import static com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY;
import static com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY;

/**
 * User credential settings. Stored secrets are never returned to the client.
 */
public class GitCredentialsProperties {

    /**
     * Whether a credential is stored.
     */
    @JsonProperty(access = READ_ONLY)
    private boolean configured;

    /**
     * HTTPS origin to which these credentials may be sent.
     */
    private String origin;

    /**
     * Git authentication username.
     */
    private String username;

    /**
     * New password/token; empty or omitted preserves the existing secret.
     */
    @JsonProperty(access = WRITE_ONLY)
    private String secret;

    /**
     * Explicitly remove the stored credential when saving settings.
     */
    @JsonProperty(access = WRITE_ONLY)
    private boolean removeCredentials;

    public boolean isConfigured() {
        return configured;
    }

    public void setConfigured(boolean configured) {
        this.configured = configured;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public boolean isRemoveCredentials() {
        return removeCredentials;
    }

    public void setRemoveCredentials(boolean removeCredentials) {
        this.removeCredentials = removeCredentials;
    }
}
