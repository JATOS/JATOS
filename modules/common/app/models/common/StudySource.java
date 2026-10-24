package models.common;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import jakarta.persistence.*;

import java.sql.Timestamp;
import java.util.Objects;

/**
 * Study source configuration and latest successful synchronization info
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
     * Configured source kind; NONE retains update metadata without enabling synchronization.
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
    @Column(length = 255)
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

    /**
     * Time of the latest successful properties synchronization; null if none.
     */
    private Timestamp lastPropertiesSyncAt;

    /**
     * Initiating username recorded at the latest successful properties synchronization.
     */
    private String lastPropertiesSyncBy;

    /**
     * Source kind used for the latest successful properties synchronization.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Type lastPropertiesSyncSourceType;

    /**
     * Repository URL or folder path used for that properties update, preserved if settings change.
     */
    @Column(length = 4096)
    private String lastPropertiesSyncSourceLocation;

    /**
     * Resolved commit of the latest properties update; null for a folder source.
     */
    @Column(length = 64)
    private String lastPropertiesSyncGitCommit;

    /**
     * Time of the latest successful assets synchronization; null if none.
     */
    private Timestamp lastAssetsSyncAt;

    /**
     * Initiating username recorded at the latest successful assets synchronization.
     */
    @Column(length = 255)
    private String lastAssetsSyncBy;

    /**
     * Source kind used for the latest successful assets synchronization.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Type lastAssetsSyncSourceType;

    /**
     * Repository URL or folder path used for that assets update, preserved if settings change.
     */
    @Column(length = 4096)
    private String lastAssetsSyncSourceLocation;

    /**
     * Resolved commit of the latest assets update; null for a folder source.
     */
    @Column(length = 64)
    private String lastAssetsSyncGitCommit;

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

    /**
     * Remove configuration and credentials, retaining latest-update metadata.
     */
    public void clearConfiguration() {
        type = Type.NONE;
        gitUrl = null;
        gitRef = null;
        localFolderPath = null;
        anonymous = false;
        gitCredentials = null;
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

    public Timestamp getLastPropertiesSyncAt() {
        return lastPropertiesSyncAt;
    }

    public void setLastPropertiesSyncAt(Timestamp value) {
        lastPropertiesSyncAt = value;
    }

    public String getLastPropertiesSyncBy() {
        return lastPropertiesSyncBy;
    }

    public void setLastPropertiesSyncBy(String value) {
        lastPropertiesSyncBy = value;
    }

    public Type getLastPropertiesSyncSourceType() {
        return lastPropertiesSyncSourceType;
    }

    public void setLastPropertiesSyncSourceType(Type value) {
        lastPropertiesSyncSourceType = value;
    }

    public String getLastPropertiesSyncSourceLocation() {
        return lastPropertiesSyncSourceLocation;
    }

    public void setLastPropertiesSyncSourceLocation(String value) {
        lastPropertiesSyncSourceLocation = value;
    }

    public String getLastPropertiesSyncGitCommit() {
        return lastPropertiesSyncGitCommit;
    }

    public void setLastPropertiesSyncGitCommit(String value) {
        lastPropertiesSyncGitCommit = value;
    }

    public Timestamp getLastAssetsSyncAt() {
        return lastAssetsSyncAt;
    }

    public void setLastAssetsSyncAt(Timestamp value) {
        lastAssetsSyncAt = value;
    }

    public String getLastAssetsSyncBy() {
        return lastAssetsSyncBy;
    }

    public void setLastAssetsSyncBy(String value) {
        lastAssetsSyncBy = value;
    }

    public Type getLastAssetsSyncSourceType() {
        return lastAssetsSyncSourceType;
    }

    public void setLastAssetsSyncSourceType(Type value) {
        lastAssetsSyncSourceType = value;
    }

    public String getLastAssetsSyncSourceLocation() {
        return lastAssetsSyncSourceLocation;
    }

    public void setLastAssetsSyncSourceLocation(String value) {
        lastAssetsSyncSourceLocation = value;
    }

    public String getLastAssetsSyncGitCommit() {
        return lastAssetsSyncGitCommit;
    }

    public void setLastAssetsSyncGitCommit(String value) {
        lastAssetsSyncGitCommit = value;
    }
}
