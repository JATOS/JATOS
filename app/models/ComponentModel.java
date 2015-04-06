package models;

import java.io.File;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;

import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;

import play.data.validation.ValidationError;
import services.gui.MessagesStrings;
import utils.JsonUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;

/**
 * Domain model of a component.
 * 
 * @author Kristian Lange
 */
@Entity
public class ComponentModel {

	public static final String TITLE = "title";
	public static final String HTML_FILE_PATH = "htmlFilePath";
	public static final String JSON_DATA = "jsonData";
	public static final String RESULT = "result";
	public static final String POSITION = "position";
	public static final String RELOADABLE = "reloadable";
	public static final String COMMENTS = "comments";
	public static final String COMPONENT = "component";

	@Id
	@GeneratedValue
	@JsonView(JsonUtils.JsonForPublix.class)
	private Long id;

	/**
	 * Universally, (world-wide) unique ID. Used for import/export between
	 * different JATOS instances. A study can have only one component with the
	 * same UUID, although it is allowed to have other studies that have this
	 * component with this UUID.
	 */
	@Column(unique = true, nullable = false)
	@JsonView(JsonUtils.JsonForIO.class)
	private String uuid;

	@JsonIgnore
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "study_id")
	private StudyModel study;

	@JsonView({ JsonUtils.JsonForPublix.class, JsonUtils.JsonForIO.class })
	private String title;

	/**
	 * Timestamp of the creation or the last update of this component
	 */
	@JsonIgnore
	private Timestamp date;

	/**
	 * Local path to component's HTML file in the study assets' dir. File
	 * separators are persisted as '/'.
	 */
	@JsonView({ JsonUtils.JsonForPublix.class, JsonUtils.JsonForIO.class })
	@JoinColumn(name = "viewUrl")
	private String htmlFilePath;

	@JsonView({ JsonUtils.JsonForPublix.class, JsonUtils.JsonForIO.class })
	private boolean reloadable = false;

	/**
	 * An inactive component can't be used within a study - it generates an
	 * error message if one try. Further it's skipped if one uses
	 * startNextComponent from the public API.
	 */
	@JsonView(JsonUtils.JsonForIO.class)
	private boolean active = true;

	/**
	 * User comments, reminders, something to share with others. They have no
	 * further meaning.
	 */
	@Lob
	@JsonView({ JsonUtils.JsonForIO.class })
	private String comments;

	@JsonView({ JsonUtils.JsonForPublix.class, JsonUtils.JsonForIO.class })
	@Lob
	private String jsonData;

	public ComponentModel() {
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

	public void setStudy(StudyModel study) {
		this.study = study;
	}

	public StudyModel getStudy() {
		return this.study;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getTitle() {
		return this.title;
	}

	public void setDate(Timestamp date) {
		this.date = date;
	}

	public Timestamp getDate() {
		return this.date;
	}

	public void setHtmlFilePath(String htmlFilePath) {
		this.htmlFilePath = htmlFilePath;
	}

	public String getHtmlFilePath() {
		if (htmlFilePath != null) {
			return this.htmlFilePath.replace('/', File.separatorChar);
		} else {
			return null;
		}
	}

	public void setComments(String comments) {
		this.comments = comments;
	}

	public String getComments() {
		return this.comments;
	}

	public String getJsonData() {
		if (this.jsonData == null) {
			return null;
		}
		return JsonUtils.makePretty(jsonData);
	}

	public void setJsonData(String jsonDataStr) {
		if (jsonDataStr == null) {
			this.jsonData = null;
			return;
		}
		if (!JsonUtils.isValidJSON(jsonDataStr)) {
			// Set the invalid string anyway, but don't standardise it. It will
			// cause an error during next validate() if one tries to edit this
			// component.
			this.jsonData = jsonDataStr;
			return;
		}
		this.jsonData = JsonUtils.asStringForDB(jsonDataStr);
	}

	public boolean isReloadable() {
		return reloadable;
	}

	public void setReloadable(boolean reloadable) {
		this.reloadable = reloadable;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public List<ValidationError> validate() {
		List<ValidationError> errorList = new ArrayList<ValidationError>();
		if (title == null || title.trim().isEmpty()) {
			errorList.add(new ValidationError(TITLE,
					MessagesStrings.MISSING_TITLE));
		}
		if (title != null && !Jsoup.isValid(title, Whitelist.none())) {
			errorList.add(new ValidationError(TITLE,
					MessagesStrings.NO_HTML_ALLOWED));
		}
		if (htmlFilePath != null && !htmlFilePath.trim().isEmpty()) {
			String pathRegEx = "^(\\w+)(\\/\\w+)?\\.\\w+(\\?(\\w+=[\\w\\d]+(&\\w+=[\\w\\d]+)+)+)*$";
			if (!(htmlFilePath.matches(pathRegEx) || htmlFilePath.trim()
					.isEmpty())) {
				errorList
						.add(new ValidationError(
								HTML_FILE_PATH,
								MessagesStrings.NOT_A_VALID_PATH_YOU_CAN_LEAVE_IT_EMPTY));
			}
		}
		if (comments != null && !Jsoup.isValid(comments, Whitelist.none())) {
			errorList.add(new ValidationError(COMMENTS,
					MessagesStrings.NO_HTML_ALLOWED));
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
		if (!(obj instanceof ComponentModel)) {
			return false;
		}
		ComponentModel other = (ComponentModel) obj;
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
