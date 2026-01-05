package models.common.legacy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import daos.common.worker.WorkerType;
import models.common.Component;
import models.common.Study;
import models.common.User;
import models.common.workers.WorkerTypeConverter;
import utils.common.JsonUtils;

import javax.persistence.*;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Old model kept for deserialization JSON of old versions!
 *
 * @author Kristian Lange (2014)
 */
public class StudyV2 {

    @Id
    @GeneratedValue
    @JsonView(JsonUtils.JsonForPublix.class)
    private Long id;

    /**
     * Universally unique ID. Used for import/export between different JATOS instances. On one JATOS
     * instance it is only allowed to have one study with the same UUID.
     */
    @Column(unique = true, nullable = false)
    @JsonView(JsonUtils.JsonForIO.class)
    private String uuid;

    @JsonView({ JsonUtils.JsonForPublix.class, JsonUtils.JsonForIO.class })
    private String title;

    @Lob
    @JsonView({ JsonUtils.JsonForPublix.class, JsonUtils.JsonForIO.class })
    private String description;

    /**
     * Timestamp of the creation or the last update of this study
     */
    @JsonIgnore
    private Timestamp date;

    /**
     * If a study is locked, it can't be changed.
     */
    @JsonView(JsonUtils.JsonForPublix.class)
    private boolean locked = false;

    /**
     * List of worker types that are allowed to run this study. If the worker type is not in this list, it has no
     * permission to run this study.
     */
    @JsonView(JsonUtils.JsonForIO.class)
    @ElementCollection
    @Convert(converter = WorkerTypeConverter.class)
    private Set<WorkerType> allowedWorkerList = new HashSet<>();

    /**
     * Study assets directory name
     */
    @JsonView({ JsonUtils.JsonForIO.class, JsonUtils.JsonForPublix.class })
    private String dirName;

    /**
     * User comments, reminders, something to share with others. They have no further meaning.
     */
    @Lob
    @JsonView({ JsonUtils.JsonForIO.class })
    private String comments;

    /**
     * Data in JSON format that are responded after public APIs 'getData' call.
     */
    @Lob
    @JsonView({ JsonUtils.JsonForPublix.class, JsonUtils.JsonForIO.class })
    private String jsonData;

    /**
     * List of users that are members of this study (have access rights).
     */
    @JsonIgnore
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "StudyUserMap", joinColumns = {
            @JoinColumn(name = "study_id", referencedColumnName = "id") }, inverseJoinColumns = {
            @JoinColumn(name = "user_username", referencedColumnName = "username") })
    private Set<User> userList = new HashSet<>();

    /**
     * Ordered list of component of this study
     */
    @JsonView(JsonUtils.JsonForIO.class)
    @OneToMany(fetch = FetchType.LAZY)
    @OrderColumn(name = "componentList_order")
    @JoinColumn(name = "study_id")
    // Not using mappedBy because of
    // http://stackoverflow.com/questions/2956171/jpa-2-0-ordercolumn-annotation-in-hibernate-3-5
    private List<Component> componentList = new ArrayList<>();

    public StudyV2() {
        // Add default allowed workers
        addAllowedWorker(WorkerType.JATOS);
        addAllowedWorker(WorkerType.PERSONAL_SINGLE);
        addAllowedWorker(WorkerType.PERSONAL_MULTIPLE);
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

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTitle() {
        return this.title;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDescription() {
        return this.description;
    }

    public void setDirName(String dirName) {
        this.dirName = dirName;
    }

    public String getDirName() {
        return this.dirName;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public String getComments() {
        return this.comments;
    }

    public void setDate(Timestamp timestamp) {
        this.date = timestamp;
    }

    public Timestamp getDate() {
        return this.date;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public boolean isLocked() {
        return this.locked;
    }

    public String getJsonData() {
        return jsonData;
    }

    public void setJsonData(String jsonData) {
        this.jsonData = jsonData;
    }

    public void setAllowedWorkerList(Set<WorkerType> allowedWorkerList) {
        this.allowedWorkerList = allowedWorkerList;
    }

    public Set<WorkerType> getAllowedWorkerList() {
        return this.allowedWorkerList;
    }

    public void addAllowedWorker(WorkerType workerType) {
        allowedWorkerList.add(workerType);
    }

    public void setUserList(Set<User> userList) {
        this.userList = userList;
    }

    public Set<User> getUserList() {
        return userList;
    }

    public void setComponentList(List<Component> componentList) {
        this.componentList = componentList;
    }

    public List<Component> getComponentList() {
        return this.componentList;
    }

    public Study toStudy() {
        Study study = new Study();
        study.setUuid(getUuid());
        study.setTitle(getTitle());
        study.setDescription(getDescription());
        study.setDate(getDate());
        study.setLocked(isLocked());
        study.setDirName(getDirName());
        study.setComments(getComments());
        study.setJsonData(getJsonData());
        study.setComponentList(getComponentList());
        return study;
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

        if (!(obj instanceof StudyV2)) return false;

        StudyV2 other = (StudyV2) obj;
        if (getId() == null) return other.getId() == null;
        return getId().equals(other.getId());
    }

}
