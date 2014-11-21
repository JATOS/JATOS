package models;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.persistence.CascadeType;
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
import javax.persistence.TypedQuery;

import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;

import play.data.validation.ValidationError;
import play.db.jpa.JPA;
import services.ErrorMessages;
import services.IOUtils;
import services.JsonUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;

/**
 * Domain model and DAO of a study.
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

	@Id
	@GeneratedValue
	@JsonView(JsonUtils.JsonForPublix.class)
	private Long id;

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
	 * Directory name of this study
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
	}

	/**
	 * Constructor for cloning (without members)
	 */
	public StudyModel(StudyModel study) {
		// Don't clone members and field 'locked'
		this.description = study.description;
		this.dirName = study.dirName;
		this.jsonData = study.jsonData;
		this.title = study.title;
		this.locked = false;
		ComponentModel clone;
		for (ComponentModel component : study.componentList) {
			clone = new ComponentModel(component);
			clone.setStudy(this);
			componentList.add(clone);
		}
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getId() {
		return this.id;
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
		if (this.jsonData == null) {
			return null;
		}
		return JsonUtils.makePretty(jsonData);
	}

	public void setJsonData(String jsonDataStr) {
		if (!JsonUtils.isValidJSON(jsonDataStr)) {
			// Set the invalid string anyway. It will cause an error during
			// validate().
			this.jsonData = jsonDataStr;
			return;
		}
		this.jsonData = JsonUtils.asStringForDB(jsonDataStr);
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

	public void componentOrderMinusOne(ComponentModel component) {
		int index = componentList.indexOf(component);
		if (index > 0) {
			ComponentModel prevComponent = componentList.get(index - 1);
			componentOrderSwap(component, prevComponent);
		}
	}

	public void componentOrderPlusOne(ComponentModel component) {
		int index = componentList.indexOf(component);
		if (index < (componentList.size() - 1)) {
			ComponentModel nextComponent = componentList.get(index + 1);
			componentOrderSwap(component, nextComponent);
		}
	}

	public void componentOrderSwap(ComponentModel component1,
			ComponentModel component2) {
		int index1 = componentList.indexOf(component1);
		int index2 = componentList.indexOf(component2);
		ComponentModel.changeComponentOrder(component1, index2);
		ComponentModel.changeComponentOrder(component2, index1);
	}

	public List<ValidationError> validate() {
		List<ValidationError> errorList = new ArrayList<ValidationError>();
		if (title == null || title.isEmpty()) {
			errorList.add(new ValidationError(TITLE,
					ErrorMessages.MISSING_TITLE));
		}
		if (title != null && !Jsoup.isValid(title, Whitelist.none())) {
			errorList.add(new ValidationError(TITLE,
					ErrorMessages.NO_HTML_ALLOWED));
		}
		if (description != null
				&& !Jsoup.isValid(description, Whitelist.none())) {
			errorList.add(new ValidationError(DESCRIPTION,
					ErrorMessages.NO_HTML_ALLOWED));
		}
		if (dirName == null || dirName.isEmpty()) {
			errorList.add(new ValidationError(DIRNAME,
					ErrorMessages.MISSING_DIRNAME));
		}
		Pattern pattern = Pattern.compile(IOUtils.REGEX_ILLEGAL_IN_FILENAME);
		Matcher matcher = pattern.matcher(dirName);
		if (dirName != null && matcher.find()) {
			errorList.add(new ValidationError(DIRNAME,
					ErrorMessages.INVALID_DIR_NAME));
		}
		if (jsonData != null && !JsonUtils.isValidJSON(jsonData)) {
			errorList.add(new ValidationError(JSON_DATA,
					ErrorMessages.INVALID_JSON_FORMAT));
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

	public static StudyModel findById(Long id) {
		return JPA.em().find(StudyModel.class, id);
	}

	public static List<StudyModel> findAll() {
		TypedQuery<StudyModel> query = JPA.em().createQuery(
				"SELECT e FROM StudyModel e", StudyModel.class);
		return query.getResultList();
	}

	public static List<StudyModel> findAllByUser(String memberEmail) {
		TypedQuery<StudyModel> query = JPA.em().createQuery(
				"SELECT DISTINCT g FROM UserModel u LEFT JOIN u.studyList g "
						+ "WHERE u.email = :member", StudyModel.class);
		query.setParameter("member", memberEmail);
		List<StudyModel> studyList = query.getResultList();
		// Sometimes the DB returns an element that's just null (bug?). Iterate
		// through the list and remove all null elements.
		Iterator<StudyModel> it = studyList.iterator();
		while (it.hasNext()) {
			StudyModel study = it.next();
			if (study == null) {
				it.remove();
			}
		}
		return studyList;
	}

	public void persist() {
		JPA.em().persist(this);
	}

	public void merge() {
		JPA.em().merge(this);
	}

	public void remove() {
		JPA.em().remove(this);
	}

	public void refresh() {
		JPA.em().refresh(this);
	}

}
