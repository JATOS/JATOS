package models.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreType;
import jakarta.persistence.*;

import java.sql.Timestamp;
import java.util.Objects;

/**
 * HTTPS credentials owned exclusively by a study source or a user. Never serialize this entity.
 */
@Entity
@Table(name = "GitCredentials")
@JsonIgnoreType
public class GitCredentials {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "study_source_id", unique = true,
            foreignKey = @ForeignKey(name = "FK_git_credentials_source"))
    private StudySource studySource;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_username", unique = true,
            foreignKey = @ForeignKey(name = "FK_git_credentials_user"))
    private User user;

    @Column(nullable = false)
    private String username;

    /**
     * Versioned encrypted payload including nonce and authentication tag; never plaintext.
     */
    @Lob
    @Column(nullable = false)
    private String encryptedSecret;

    /**
     * HTTPS scheme, hostname and optional port, without a repository path (e.g. https://git.example.org:8443).
     */
    @Column(nullable = false, length = 512)
    private String origin;

    @Column(nullable = false, updatable = false)
    private Timestamp createdAt = new Timestamp(System.currentTimeMillis());

    protected GitCredentials() {
    }

    public GitCredentials(StudySource source) {
        studySource = Objects.requireNonNull(source);
    }

    public GitCredentials(User user) {
        this.user = Objects.requireNonNull(user);
    }

    @PrePersist
    @PreUpdate
    private void validateOwner() {
        if ((studySource == null) == (user == null))
            throw new IllegalStateException("Credentials require exactly one owner: study source or user");
    }

    public Long getId() {
        return id;
    }

    public StudySource getStudySource() {
        return studySource;
    }

    public User getUser() {
        return user;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String value) {
        username = value;
    }

    @JsonIgnore
    public String getEncryptedSecret() {
        return encryptedSecret;
    }

    public void setEncryptedSecret(String value) {
        encryptedSecret = value;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String value) {
        origin = value;
    }
}
