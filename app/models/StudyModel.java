package models;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.persistence.CascadeType;
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

import models.workers.ClosedStandaloneWorker;
import models.workers.JatosWorker;
import models.workers.TesterWorker;

import org.hibernate.annotations.GenericGenerator;
import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;

import play.data.validation.ValidationError;
import services.gui.MessagesStrings;
import utils.IOUtils;
import utils.JsonUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;

/**
 * Domain model of a study.
 * 
 * @author Kristian Lange
 */
@Entity
public class StudyModel {

	public static final String MEMBERS = "user";
	public static final String TITLE = "title";
	public static final String JSON_DATA = "jsonData";
	public static final String DESCRIPTION = "description";
	public static final String DIRNAME = "dirName";
	public static final String STUDY = "study";
	public static final String ALLOWED_WORKER_LIST = "allowedWorkerList";

	@Id
	@GeneratedValue
	@JsonView(JsonUtils.JsonForPublix.class)
	private Long id;

	/**
	 * Universally unique ID. Used for import/export between different JATOS
	 * instances.
	 */
	@JsonView(JsonUtils.JsonForIO.class)
	@GeneratedValue(generator = "uuid2")
	@GenericGenerator(name = "uuid2", strategy = "uuid2")
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
	private Set<String> allowedWorkerList = new HashSet<String>();

	/**
	 * Study assets directory name
	 */
	@JsonView(JsonUtils.JsonForIO.class)
	private String dirName;

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
	@JoinTable(name = "StudyMemberMap", joinColumns = { @JoinColumn(name = "study_id", referencedColumnName = "id") }, inverseJoinColumns = { @JoinColumn(name = "member_email", referencedColumnName = "email") })
	private Set<UserModel> memberList = new HashSet<UserModel>();

	/**
	 * Ordered list of component of this study
	 */
	@JsonView(JsonUtils.JsonForIO.class)
	@OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	@OrderColumn(name = "componentList_order")
	@JoinColumn(name = "study_id")
	private List<ComponentModel> componentList = new ArrayList<ComponentModel>();

	public StudyModel() {
		// Add default allowed workers
		addAllowedWorker(JatosWorker.WORKER_TYPE);
		addAllowedWorker(TesterWorker.WORKER_TYPE);
		addAllowedWorker(ClosedStandaloneWorker.WORKER_TYPE);
	}

	/**
	 * Constructor for cloning (without members or locked)
	 */
	public StudyModel(StudyModel study) {
		// Don't clone fields 'memberList' and 'locked'
		this.description = study.description;
		this.dirName = study.dirName;
		this.jsonData = study.jsonData;
		this.title = study.title;
		this.locked = false;
		for (String worker : study.allowedWorkerList) {
			this.allowedWorkerList.add(worker);
		}
		for (ComponentModel component : study.componentList) {
			ComponentModel clone = new ComponentModel(component);
			clone.setStudy(this);
			this.componentList.add(clone);
		}
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
		if (jsonData == null) {
			return null;
		}
		if (!JsonUtils.isValidJSON(jsonData)) {
			return jsonData;
		}
		return JsonUtils.makePretty(jsonData);
	}

	public void setJsonData(String jsonDataStr) {
		if (jsonDataStr == null) {
			this.jsonData = null;
			return;
		}
		if (!JsonUtils.isValidJSON(jsonDataStr)) {
			// Set the invalid string anyway. It will cause an error during
			// validate().
			this.jsonData = jsonDataStr;
			return;
		}
		this.jsonData = JsonUtils.asStringForDB(jsonDataStr);
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

	public void setMemberList(Set<UserModel> memberList) {
		this.memberList = memberList;
	}

	public Set<UserModel> getMemberList() {
		return memberList;
	}

	public void addMember(UserModel user) {
		memberList.add(user);
	}

	public void removeMember(UserModel user) {
		memberList.remove(user);
	}

	public boolean hasMember(UserModel user) {
		return memberList.contains(user);
	}

	public void setComponentList(List<ComponentModel> componentList) {
		this.componentList = componentList;
	}

	public List<ComponentModel> getComponentList() {
		return this.componentList;
	}

	/**
	 * Gets the component of this study at the given position. The smallest
	 * position is 1 (and not 0 as in an array).
	 */
	public ComponentModel getComponent(int position) {
		return componentList.get(position - 1);
	}

	/**
	 * Returns the position (index+1) of the component in the list of components
	 * of this study or null if it doesn't exist.
	 */
	public Integer getComponentPosition(ComponentModel component) {
		int index = componentList.indexOf(component);
		if (index != -1) {
			return index + 1;
		} else {
			return null;
		}
	}

	public void addComponent(ComponentModel component) {
		componentList.add(component);
	}

	public void removeComponent(ComponentModel component) {
		componentList.remove(component);
	}

	public boolean hasComponent(ComponentModel component) {
		return componentList.contains(component);
	}

	@JsonIgnore
	public ComponentModel getFirstComponent() {
		if (componentList.size() > 0) {
			return componentList.get(0);
		}
		return null;
	}

	@JsonIgnore
	public ComponentModel getNextComponent(ComponentModel component) {
		int index = componentList.indexOf(component);
		if (index < componentList.size() - 1) {
			return componentList.get(index + 1);
		}
		return null;
	}

	public List<ValidationError> validate() {
		List<ValidationError> errorList = new ArrayList<ValidationError>();
		if (title == null || title.isEmpty()) {
			errorList.add(new ValidationError(TITLE,
					MessagesStrings.MISSING_TITLE));
		}
		if (title != null && !Jsoup.isValid(title, Whitelist.none())) {
			errorList.add(new ValidationError(TITLE,
					MessagesStrings.NO_HTML_ALLOWED));
		}
		if (description != null
				&& !Jsoup.isValid(description, Whitelist.none())) {
			errorList.add(new ValidationError(DESCRIPTION,
					MessagesStrings.NO_HTML_ALLOWED));
		}
		if (dirName == null || dirName.isEmpty()) {
			errorList.add(new ValidationError(DIRNAME,
					MessagesStrings.MISSING_DIRNAME));
		}
		Pattern pattern = Pattern.compile(IOUtils.REGEX_ILLEGAL_IN_FILENAME);
		Matcher matcher = pattern.matcher(dirName);
		if (dirName != null && matcher.find()) {
			errorList.add(new ValidationError(DIRNAME,
					MessagesStrings.INVALID_DIR_NAME));
		}
		if (jsonData != null && !JsonUtils.isValidJSON(jsonData)) {
			errorList.add(new ValidationError(JSON_DATA,
					MessagesStrings.INVALID_JSON_FORMAT));
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
		if (!(obj instanceof StudyModel)) {
			return false;
		}
		StudyModel other = (StudyModel) obj;
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
