package models.common.legacy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import com.google.common.base.Strings;
import general.common.MessagesStrings;
import models.common.Component;
import models.common.User;
import models.common.workers.JatosWorker;
import models.common.workers.PersonalMultipleWorker;
import models.common.workers.PersonalSingleWorker;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import play.data.validation.ValidationError;
import utils.common.IOUtils;
import utils.common.JsonUtils;

import javax.persistence.*;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Old model kept for unmarshaling JSON of old versions!
 * <p>
 * Model for a DB entity of a study with all properties of a study but not the results of a study. The results of a
 * study are stored in StudyResults and ComponentResult. A study consists of a list components and their model is
 * Component. Default values, where necessary, are at the fields or in the constructor. For the GUI model
 * StudyProperties is used.
 *
 * @author Kristian Lange (2014)
 */
public class StudyV2 {

    /**
     * Version of this model used for serialisation (e.g. JSON marshaling)
     */
    public static final String SERIAL_VERSION = "2";

    public static final String ID = "id";
    public static final String UUID = "uuid";
    public static final String MEMBERS = "user";
    public static final String TITLE = "title";
    public static final String JSON_DATA = "jsonData";
    public static final String DESCRIPTION = "description";
    public static final String DIR_NAME = "dirName";
    public static final String COMMENTS = "comments";
    public static final String STUDY = "study";
    public static final String ALLOWED_WORKER_LIST = "allowedWorkerList";

    @Id
    @GeneratedValue
    @JsonView(JsonUtils.JsonForPublix.class)
    private Long id;

    /**
     * Universally (world-wide) unique ID. Used for import/export between different JATOS instances. On one JATOS
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
    private Set<String> allowedWorkerList = new HashSet<>();

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
        addAllowedWorker(JatosWorker.WORKER_TYPE);
        addAllowedWorker(PersonalMultipleWorker.WORKER_TYPE);
        addAllowedWorker(PersonalSingleWorker.WORKER_TYPE);
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

    public void setAllowedWorkerList(Set<String> allowedWorkerList) {
        this.allowedWorkerList = allowedWorkerList;
    }

    public Set<String> getAllowedWorkerList() {
        return this.allowedWorkerList;
    }

    public void addAllowedWorker(String workerType) {
        allowedWorkerList.add(workerType);
    }

    public void removeAllowedWorker(String workerType) {
        allowedWorkerList.remove(workerType);
    }

    public boolean hasAllowedWorker(String workerType) {
        return allowedWorkerList.contains(workerType);
    }

    public void setUserList(Set<User> userList) {
        this.userList = userList;
    }

    public Set<User> getUserList() {
        return userList;
    }

    public void addMember(User user) {
        userList.add(user);
    }

    public void removeMember(User user) {
        userList.remove(user);
    }

    public boolean hasMember(User user) {
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
        if (index != -1) {
            return index + 1;
        } else {
            return null;
        }
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
    public Component getFirstComponent() {
        if (componentList.size() > 0) {
            return componentList.get(0);
        }
        return null;
    }

    @JsonIgnore
    public Component getLastComponent() {
        if (componentList.size() > 0) {
            return componentList.get(componentList.size() - 1);
        }
        return null;
    }

    @JsonIgnore
    public Component getNextComponent(Component component) {
        int index = componentList.indexOf(component);
        if (index < componentList.size() - 1) {
            return componentList.get(index + 1);
        }
        return null;
    }

    public List<ValidationError> validate() {
        List<ValidationError> errorList = new ArrayList<>();
        if (title == null || title.trim().isEmpty()) {
            errorList.add(new ValidationError(TITLE, MessagesStrings.MISSING_TITLE));
        }
        if (title != null && !Jsoup.isValid(title, Safelist.none())) {
            errorList.add(new ValidationError(TITLE, MessagesStrings.NO_HTML_ALLOWED));
        }
        if (description != null && !Jsoup.isValid(description, Safelist.none())) {
            errorList.add(new ValidationError(DESCRIPTION, MessagesStrings.NO_HTML_ALLOWED));
        }
        if (dirName == null || dirName.trim().isEmpty()) {
            errorList.add(new ValidationError(DIR_NAME, MessagesStrings.MISSING_DIR_NAME));
        }
        Pattern pattern = Pattern.compile(IOUtils.REGEX_ILLEGAL_IN_FILENAME);
        Matcher matcher = pattern.matcher(dirName);
        if (dirName != null && matcher.find()) {
            errorList.add(new ValidationError(DIR_NAME, MessagesStrings.INVALID_DIR_NAME));
        }
        if (comments != null && !Jsoup.isValid(comments, Safelist.none())) {
            errorList.add(new ValidationError(COMMENTS, MessagesStrings.NO_HTML_ALLOWED));
        }
        if (!Strings.isNullOrEmpty(jsonData) && !JsonUtils.isValid(jsonData)) {
            errorList.add(new ValidationError(JSON_DATA, MessagesStrings.INVALID_JSON_FORMAT));
        }
        return errorList.isEmpty() ? null : errorList;
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
