package models.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import com.google.common.base.Strings;
import utils.common.HashUtils;
import utils.common.JsonUtils.JsonForApi;
import utils.common.JsonUtils.JsonForPublix;

import javax.persistence.*;
import java.sql.Timestamp;
import java.util.*;

import static utils.common.JsonUtils.JsonForIO;
import static utils.common.JsonUtils.asStringForDB;

/**
 * DB entity of a study. Used for JSON marshalling and JPA persistance.
 *
 * This entity has all properties of a study but not the results of a study. The results of a study are stored in
 * StudyResults and ComponentResult. A study consists of a list components and their model is Component. It can have
 * several Batches.
 *
 * Default values, where necessary, are at the fields or in the constructor.
 *
 * For the GUI a different model (models.gui.StudyProperties) is used.
 *
 * @author Kristian Lange
 */
@Entity
@Table(name = "Study")
public class Study {

    /**
     * Version of this model used for serialisation (e.g. JSON marshaling)
     */
    public static final int SERIAL_VERSION = 3;

    public static final String USERS = "users";
    public static final String STUDY = "study";

    @Id
    @GeneratedValue
    @JsonView({JsonForPublix.class, JsonForApi.class})
    private Long id;

    /**
     * Universally (world-wide) unique ID. Used for import/export between different JATOS instances. On one JATOS
     * instance it is only allowed to have one study with the same UUID.
     */
    @Column(unique = true, nullable = false)
    @JsonView({JsonForPublix.class, JsonForIO.class, JsonForApi.class})
    private String uuid;

    @JsonView({JsonForPublix.class, JsonForIO.class, JsonForApi.class})
    private String title;

    @Lob
    @JsonView({JsonForPublix.class, JsonForIO.class})
    private String description;

    /**
     * Timestamp of the creation or the last update of this study
     */
    @JsonIgnore
    private Timestamp date;

    /**
     * If a study is locked, it can't be changed.
     */
    @JsonView({JsonForPublix.class, JsonForApi.class})
    private boolean locked = false;

    /**
     * A deactivated study cannot be run by worker. A study can be deactivated by an admin, but not by the member users
     * of the study (unless they are admin). It's meant to give admins the possibility to turn off studies that use to
     * many resources. By default, it's activated (true).
     */
    @JsonView({JsonForApi.class})
    private boolean active = true;

    /**
     * Is this study a group study (e.g. group members can send messages to each other)
     */
    @JsonView({JsonForIO.class, JsonForPublix.class, JsonForApi.class})
    private boolean groupStudy = false;

    /**
     * A study with a linear study flow allows the component position to only increase or stay the same
     * (no going back to earlier components).
     */
    @JsonView({JsonForIO.class, JsonForPublix.class, JsonForApi.class})
    private boolean linearStudy = false;

    /**
     * If true a preview of a study run of this study is allowed: the study link can be used many times as long as
     * it does not go further than the first component. As soon as the second component is reached the usual
     * restrictions of the worker apply. 'Single' workers only (PersonalSingleWorker or MultipleSingleWorker).
     */
    @JsonView({JsonForIO.class, JsonForPublix.class, JsonForApi.class})
    private boolean allowPreview = false;

    /**
     * Study assets directory name
     */
    @JsonView({JsonForIO.class, JsonForPublix.class, JsonForApi.class})
    private String dirName;

    /**
     * User comments, reminders, something to share with others. They have no further meaning.
     */
    @Lob
    @JsonView({JsonForIO.class, JsonForApi.class})
    private String comments;

    /**
     * Data in JSON format: every study run of this Study gets access to them. They can be changed in the GUI but not
     * via jatos.js. Can be used for initial data and configuration.
     */
    @Lob
    @JsonView({JsonForPublix.class, JsonForIO.class, JsonForApi.class})
    private String jsonData;

    /**
     * URL to which should be redirected if the study run finishes. If kept null it won't be redirected and the default
     * endPage will be shown.
     */
    @JsonView({JsonForIO.class, JsonForApi.class})
    private String endRedirectUrl;

    /**
     * Will be shown to the worker on the Study Entry page
     */
    @JsonView({JsonForIO.class, JsonForApi.class})
    private String studyEntryMsg;

    /**
     * List of users that are users of this study (have access rights). The relationship is bidirectional.
     */
    @JsonIgnore
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "StudyUserMap", joinColumns = {
            @JoinColumn(name = "study_id", referencedColumnName = "id") }, inverseJoinColumns = {
            @JoinColumn(name = "user_username", referencedColumnName = "username") })
    private Set<User> userList = new HashSet<>();

    /**
     * Ordered list of component of this study. The relationship is bidirectional.
     */
    @JsonView({JsonForIO.class, JsonForApi.class})
    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn(name = "componentList_order")
    @JoinColumn(name = "study_id")
    // Not using mappedBy because of
    // http://stackoverflow.com/questions/2956171/jpa-2-0-ordercolumn-annotation-in-hibernate-3-5
    private List<Component> componentList = new ArrayList<>();

    /**
     * Ordered list of batches of this study. The relationship is bidirectional.
     */
    @JsonView({JsonForIO.class, JsonForApi.class})
    @OneToMany(fetch = FetchType.LAZY)
    @OrderColumn(name = "batchList_order")
    @JoinColumn(name = "study_id")
    // Not using mappedBy because of
    // http://stackoverflow.com/questions/2956171/jpa-2-0-ordercolumn-annotation-in-hibernate-3-5
    private List<Batch> batchList = new ArrayList<>();

    public Study() {
        this.uuid = UUID.randomUUID().toString();
        this.dirName = this.uuid;
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

    @JsonView({JsonForPublix.class, JsonForApi.class})
    public String getDescriptionHash() {
        return !Strings.isNullOrEmpty(getDescription()) ? HashUtils.getHash(getDescription(), HashUtils.SHA_256) : null;
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

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isGroupStudy() {
        return groupStudy;
    }

    public void setGroupStudy(boolean groupStudy) {
        this.groupStudy = groupStudy;
    }

    public boolean isLinearStudy() {
        return linearStudy;
    }

    public void setLinearStudy(boolean linearStudy) {
        this.linearStudy = linearStudy;
    }

    public boolean isAllowPreview() {
        return allowPreview;
    }

    public void setAllowPreview(boolean allowPreview) {
        this.allowPreview = allowPreview;
    }

    public String getJsonData() {
        return jsonData;
    }

    public void setJsonData(String jsonData) {
        this.jsonData = asStringForDB(jsonData);
    }

    public String getEndRedirectUrl() {
        return endRedirectUrl;
    }

    public void setEndRedirectUrl(String endRedirectUrl) {
        this.endRedirectUrl = endRedirectUrl;
    }

    public String getStudyEntryMsg() {
        return studyEntryMsg;
    }

    public void setStudyEntryMsg(String studyEntryMsg) {
        this.studyEntryMsg = studyEntryMsg;
    }

    public void setUserList(Set<User> userList) {
        this.userList = userList;
    }

    public Set<User> getUserList() {
        return userList;
    }

    public void addUser(User user) {
        userList.add(user);
    }

    public void removeUser(User user) {
        userList.remove(user);
    }

    public boolean hasUser(User user) {
        return userList.contains(user);
    }

    public void setComponentList(List<Component> componentList) {
        this.componentList = componentList;
    }

    public List<Component> getComponentList() {
        return this.componentList;
    }

    /**
     * Gets the component of this study at the given position. The smallest position is 1 (and not 0 as in an array).
     */
    public Component getComponent(int position) {
        return componentList.get(position - 1);
    }

    /**
     * Returns the position (index+1) of the component in the list of components of this study or null if it doesn't
     * exist.
     */
    public Integer getComponentPosition(Component component) {
        int index = componentList.indexOf(component);
        return index != -1 ? index + 1 : null;
    }

    public void addComponent(Component component) {
        componentList.add(component);
    }

    public void removeComponent(Component component) {
        componentList.remove(component);
    }

    public boolean hasComponent(Component component) {
        return componentList.contains(component);
    }

    @JsonIgnore
    public Optional<Component> getFirstComponent() {
        if (componentList.size() > 0) {
            return Optional.of(componentList.get(0));
        } else {
            return Optional.empty();
        }
    }

    @JsonIgnore
    public Optional<Component> getLastComponent() {
        if (componentList.size() > 0) {
            return Optional.of(componentList.get(componentList.size() - 1));
        } else {
            return Optional.empty();
        }
    }

    @JsonIgnore
    public Optional<Component> getNextComponent(Component component) {
        int index = componentList.indexOf(component);
        if (index < componentList.size() - 1) {
            return Optional.of(componentList.get(index + 1));
        } else {
            return Optional.empty();
        }
    }

    public void setBatchList(List<Batch> batchList) {
        this.batchList = batchList;
    }

    public List<Batch> getBatchList() {
        return this.batchList;
    }

    public boolean hasBatch(Batch batch) {
        return batchList.contains(batch);
    }

    public void addBatch(Batch batch) {
        batchList.add(batch);
    }

    public void removeBatch(Batch batch) {
        batchList.remove(batch);
    }

    @JsonIgnore
    public Batch getDefaultBatch() {
        return this.batchList.get(0);
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

        if (!(obj instanceof Study)) return false;

        Study other = (Study) obj;
        if (getId() == null) return other.getId() == null;
        return getId().equals(other.getId());
    }

}
