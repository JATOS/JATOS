package models.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import models.common.workers.Worker;
import utils.common.JsonUtils;

import javax.persistence.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Model of a DB entity of a batch. The corresponding UI model is
 * BatchProperties in model Gui.
 * <p>
 * Defines the constrains regarding workers for a batch of a study, e.g. which
 * worker types are allowed, how many workers, which Workers etc.
 *
 * @author Kristian Lange
 */
@Entity
@Table(name = "Batch")
public class Batch {

    @Id
    @GeneratedValue
    @JsonView({JsonUtils.JsonForPublix.class})
    private Long id;

    /**
     * Universally (world-wide) unique ID.
     */
    @Column(nullable = false)
    @JsonView(JsonUtils.JsonForIO.class)
    private String uuid;

    /**
     * Study this batch belongs to. This relationship is bidirectional.
     */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "study_id", insertable = false, updatable = false, nullable = false)
    private Study study;

    /**
     * Title of the batch
     */
    @JsonView({JsonUtils.JsonForPublix.class, JsonUtils.JsonForIO.class})
    private String title;

    /**
     * Only active (if true) batches can be used.
     */
    @JsonView({JsonUtils.JsonForIO.class})
    private boolean active = true;

    /**
     * Maximum number of workers/members in one group of this batch that are
     * active at the same time. If there is no limit in active members the value
     * is null. This property is only used if this batch belongs to a group
     * study.
     */
    @JsonView({JsonUtils.JsonForPublix.class, JsonUtils.JsonForIO.class})
    private Integer maxActiveMembers = null;

    /**
     * Maximum number of workers/members in one group of this batch in total. If
     * there is no limit in active members the value is null. This property is
     * only used if this batch belongs to a group study.
     */
    @JsonView({JsonUtils.JsonForPublix.class, JsonUtils.JsonForIO.class})
    private Integer maxTotalMembers = null;

    /**
     * Maximum number of workers in this batch in total independent of its
     * groups. If there is no limit in active members the value is null.
     * JatosWorker does not count here. The workers who belong to this batch are
     * stored in the workerList (except JatosWorkers).
     */
    @JsonView({JsonUtils.JsonForPublix.class, JsonUtils.JsonForIO.class})
    private Integer maxTotalWorkers = null;

    /**
     * Set of workers that is created in this batch. Workers can be created
     * before the study starts (PersonalMultipleWorker or PersonalSingleWorker)
     * or created on-the-fly after the study started (MTWorker,
     * GeneralSingleWorker, GeneralMultipleWorker). JatosWorker are created together with the User
     * and added to this list too. This relationship is bidirectional.
     */
    @JsonIgnore
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "BatchWorkerMap", joinColumns = {
            @JoinColumn(name = "batch_id", referencedColumnName = "id")}, inverseJoinColumns = {
            @JoinColumn(name = "worker_id", referencedColumnName = "id")})
    private Set<Worker> workerList = new HashSet<>();

    /**
     * Set of worker types that are allowed to run in this batch. If the worker
     * type is not in this list, it has no permission to run this study.
     */
    @JsonView({JsonUtils.JsonForPublix.class, JsonUtils.JsonForIO.class})
    @ElementCollection
    private Set<String> allowedWorkerTypes = new HashSet<>();

    /**
     * User comments, reminders, something to share with others. They have no
     * further meaning.
     */
    @Lob
    @JsonView({JsonUtils.JsonForIO.class})
    private String comments;

    /**
     * Data in JSON format: every study run of this Batch gets access to them.
     * They can be changed in the GUI but not via jatos.js. Can be used for
     * initial data and configuration.
     */
    @Lob
    @JsonView({JsonUtils.JsonForPublix.class, JsonUtils.JsonForIO.class})
    private String jsonData;

    /**
     * Temporary, global data storage that can be accessed via jatos.js to
     * exchange data between all study runs of this batch. All members of this
     * batch share the same batchSessionData. It's stored as a normal string in
     * the database but jatos.js converts it into JSON. We use versioning to
     * prevent concurrent changes of the data.
     */
    @JsonIgnore
    @Lob
    private String batchSessionData = "{}";

    /**
     * Current version of the batchSessionVersion. With each change of the data
     * it is increased by 1. We use versioning to prevent concurrent changes of
     * the data.
     */
    @JsonIgnore
    @Column(nullable = false)
    private Long batchSessionVersion = 1L;

    public Batch() {
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return this.id;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getUuid() {
        return this.uuid;
    }

    public void setStudy(Study study) {
        this.study = study;
    }

    public Study getStudy() {
        return this.study;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Integer getMaxActiveMembers() {
        return maxActiveMembers;
    }

    public void setMaxActiveMembers(Integer maxActiveMembers) {
        this.maxActiveMembers = maxActiveMembers;
    }

    public Integer getMaxTotalMembers() {
        return maxTotalMembers;
    }

    public void setMaxTotalMembers(Integer maxTotalMembers) {
        this.maxTotalMembers = maxTotalMembers;
    }

    public Integer getMaxTotalWorkers() {
        return maxTotalWorkers;
    }

    public void setMaxTotalWorkers(Integer maxTotalWorkers) {
        this.maxTotalWorkers = maxTotalWorkers;
    }

    public void setAllowedWorkerTypes(Set<String> allowedWorkerTypes) {
        this.allowedWorkerTypes = allowedWorkerTypes;
    }

    public Set<String> getAllowedWorkerTypes() {
        return this.allowedWorkerTypes;
    }

    public void addAllowedWorkerType(String workerType) {
        if (allowedWorkerTypes == null) {
            allowedWorkerTypes = new HashSet<>();
        }
        allowedWorkerTypes.add(workerType);
    }

    public void removeAllowedWorkerType(String workerType) {
        allowedWorkerTypes.remove(workerType);
    }

    public boolean hasAllowedWorkerType(String workerType) {
        return allowedWorkerTypes.contains(workerType);
    }

    public void setWorkerList(Set<Worker> workerList) {
        this.workerList = workerList;
    }

    public Set<Worker> getWorkerList() {
        return this.workerList;
    }

    public void addWorker(Worker worker) {
        workerList.add(worker);
    }

    public void addAllWorkers(List<Worker> workerList) {
        this.workerList.addAll(workerList);
    }

    public void removeWorker(Worker worker) {
        workerList.remove(worker);
    }

    public void removeAllWorkers(List<Worker> workerList) {
        this.workerList.removeAll(workerList);
    }

    public boolean hasWorker(Worker worker) {
        return workerList.contains(worker);
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public String getJsonData() {
        return jsonData;
    }

    public void setJsonData(String jsonData) {
        this.jsonData = JsonUtils.asStringForDB(jsonData);
    }

    public String getBatchSessionData() {
        return batchSessionData;
    }

    public void setBatchSessionData(String batchSessionData) {
        this.batchSessionData = batchSessionData;
    }

    public Long getBatchSessionVersion() {
        return batchSessionVersion;
    }

    public void setBatchSessionVersion(Long batchSessionVersion) {
        this.batchSessionVersion = batchSessionVersion;
    }

    @Override
    public String toString() {
        return id + " " + title;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getId() == null) ? 0 : getId().hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;

        if (obj == null) return false;

        if (!(obj instanceof Batch)) return false;

        Batch other = (Batch) obj;
        return getId().equals(other.getId());
    }

}
