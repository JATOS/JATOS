package models.common;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import jakarta.persistence.*;

import java.util.Objects;

/**
 * Study source configuration
 */
@Entity
@Table(name = "StudySource")
@JsonIgnoreType
public class StudySource {

    public enum Type {NONE, GIT, LOCAL_FOLDER}

    /**
     * Primary key shared with the owning study, populated through @MapsId.
     */
    @Id
    @Column(name = "study_id")
    private Long studyId;

    /**
     * Study that owns this source configuration and supplies its ID.
     */
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "study_id", foreignKey = @ForeignKey(name = "FK_study_source_study"))
    private Study study;

    /**
     * Optional credentials specific to this source; removed when detached from it.
     */
    @OneToOne(mappedBy = "studySource", cascade = CascadeType.ALL, orphanRemoval = true)
    private GitCredentials gitCredentials;

    /**
     * Configured source kind; NONE represents unconfigured settings before persistence.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Type type = Type.NONE;

    /**
     * HTTPS repository URL, without embedded credentials.
     */
    @Column(length = 2048)
    private String gitUrl;

    /**
     * Branch or tag to fetch; null or empty selects the repository default branch.
     */
    @Column()
    private String gitRef;

    /**
     * Source folder on the JATOS server, restricted to configured allowed roots.
     */
    @Column(length = 4096)
    private String localFolderPath;

    /**
     * Use Git without credentials, overriding source, owner, and global credentials.
     */
    @Column(nullable = false)
    private boolean anonymous = false;

    protected StudySource() {
    }

    public StudySource(Study study) {
        this.study = Objects.requireNonNull(study);
    }

    public Long getStudyId() {
        return studyId;
    }

    public Study getStudy() {
        return study;
    }

    public GitCredentials getGitCredentials() {
        return gitCredentials;
    }

    public void setGitCredentials(GitCredentials credentials) {
        if (credentials != null && credentials.getStudySource() != this)
            throw new IllegalArgumentException("Credentials must belong to this source");
        this.gitCredentials = credentials;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type value) {
        type = Objects.requireNonNull(value);
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

}
