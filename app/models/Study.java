package models;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.Lob;
import javax.persistence.ManyToMany;
import javax.persistence.OneToMany;
import javax.persistence.OrderColumn;
import javax.persistence.Table;

import utils.JsonUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;

/**
 * Model for a DB entity of a study with all properties of a study but not the
 * results of a study. The results of a study are stored in StudyResults and
 * ComponentResult. A study consists of a list components and their model is
 * Component. Default values, where necessary, are at the fields or in the
 * constructor. For the GUI model StudyProperties is used.
 * 
 * @author Kristian Lange (2014)
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
	@JsonView(JsonUtils.JsonForPublix.class)
	private Long id;

	/**
	 * Universally (world-wide) unique ID. Used for import/export between
	 * different JATOS instances. On one JATOS instance it is only allowed to
	 * have one study with the same UUID.
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
	 * List of worker types that are allowed to run this study. If the worker
	 * type is not in this list, it has no permission to run this study.
	 */
	@JsonView(JsonUtils.JsonForIO.class)
	@ElementCollection
	private Set<String> allowedWorkerTypeList = new HashSet<>();

	/**
	 * Study assets directory name
	 */
	@JsonView({ JsonUtils.JsonForIO.class, JsonUtils.JsonForPublix.class })
	private String dirName;

	/**
	 * User comments, reminders, something to share with others. They have no
	 * further meaning.
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
	 * List of users that are users of this study (have access rights).
	 */
	@JsonIgnore
	@ManyToMany(fetch = FetchType.LAZY)
	@JoinTable(name = "StudyUserMap", joinColumns = { @JoinColumn(name = "study_id", referencedColumnName = "id") }, inverseJoinColumns = { @JoinColumn(name = "user_email", referencedColumnName = "email") })
	private Set<User> userList = new HashSet<>();

	/**
	 * Ordered list of component of this study
	 */
	@JsonView(JsonUtils.JsonForIO.class)
	@OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	@OrderColumn(name = "componentList_order")
	@JoinColumn(name = "study_id")
	private List<Component> componentList = new ArrayList<>();
	
	@JsonIgnore
	@OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	@JoinColumn(name = "study_id")
	private List<Group> groupList = new ArrayList<>();

	public Study() {
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

	public void setAllowedWorkerTypeList(Set<String> allowedWorkerTypeList) {
		this.allowedWorkerTypeList = allowedWorkerTypeList;
	}

	public Set<String> getAllowedWorkerTypeList() {
		return this.allowedWorkerTypeList;
	}

	public void addAllowedWorkerType(String workerType) {
		allowedWorkerTypeList.add(workerType);
	}

	public void removeAllowedWorkerType(String workerType) {
		allowedWorkerTypeList.remove(workerType);
	}

	public boolean hasAllowedWorkerType(String workerType) {
		return allowedWorkerTypeList.contains(workerType);
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
	 * Gets the component of this study at the given position. The smallest
	 * position is 1 (and not 0 as in an array).
	 */
	public Component getComponent(int position) {
		return componentList.get(position - 1);
	}

	/**
	 * Returns the position (index+1) of the component in the list of components
	 * of this study or null if it doesn't exist.
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
	
	public void setGroupList(List<Group> groupList) {
		this.groupList = groupList;
	}

	public List<Group> getGroupList() {
		return this.groupList;
	}
	
	public void addGroup(Group group) {
		groupList.add(group);
	}

	public void removeGroup(Group group) {
		groupList.remove(group);
	}

	@Override
	public String toString() {
		return id + " " + title;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof Study)) {
			return false;
		}
		Study other = (Study) obj;
		if (id == null) {
			if (other.getId() != null) {
				return false;
			}
		} else if (!id.equals(other.getId())) {
			return false;
		}
		return true;
	}

}
