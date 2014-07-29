package models;

import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Query;
import javax.persistence.TypedQuery;

import play.Logger;
import play.data.validation.ValidationError;
import play.db.jpa.JPA;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

@Entity
public class MAComponent {

	// For JSON serialization: fields for public API
	public static class JsonForPublic {
	}

	// For JSON serialization: fields for MechArg
	public static class JsonForMA extends JsonForPublic {
	}

	@Id
	@GeneratedValue
	@JsonView(MAComponent.JsonForPublic.class)
	private Long id;

	@JsonIgnore
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "study_id")
	private MAStudy study;

	@JsonView(MAComponent.JsonForPublic.class)
	private String title;

	/**
	 * Timestamp of the creation or the last update of this component
	 */
	@JsonView(MAComponent.JsonForMA.class)
	private Timestamp date;

	@JsonView(MAComponent.JsonForPublic.class)
	private String viewUrl; // URL or local path

	@JsonView(MAComponent.JsonForPublic.class)
	private boolean reloadable;

	@JsonView(MAComponent.JsonForPublic.class)
	@Lob
	private String jsonData;

	@JsonView(MAComponent.JsonForMA.class)
	@OneToMany(mappedBy = "component", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	private List<MAResult> resultList = new ArrayList<MAResult>();

	public MAComponent() {
	}

	public void update(String title, boolean reloadable, String viewUrl,
			String jsonData) {
		this.title = title;
		this.reloadable = reloadable;
		this.viewUrl = viewUrl;
		setJsonData(jsonData);
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getId() {
		return this.id;
	}

	public void setStudy(MAStudy study) {
		this.study = study;
	}

	public MAStudy getStudy() {
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

	public void setViewUrl(String viewUrl) {
		this.viewUrl = viewUrl;
	}

	public String getViewUrl() {
		return this.viewUrl;
	}

	public String getJsonData() {
		if (this.jsonData == null) {
			return null;
		}

		// Try to make it pretty
		String jsonDataPretty = null;
		try {
			ObjectMapper mapper = new ObjectMapper();
			Object json = mapper.readValue(this.jsonData, Object.class);
			jsonDataPretty = mapper.writerWithDefaultPrettyPrinter()
					.writeValueAsString(json);
		} catch (Exception e) {
			Logger.info("getJsonData: ", e);
		}
		return jsonDataPretty;
	}

	public void setJsonData(String jsonData) {
		if (!isValidJSON(jsonData)) {
			return;
		}
		try {
			ObjectMapper mapper = new ObjectMapper();
			this.jsonData = mapper.readTree(jsonData).toString();
		} catch (Exception e) {
			Logger.info("setJsonData: ", e);
		}
	}

	public void setResultList(List<MAResult> resultList) {
		this.resultList = resultList;
	}

	public List<MAResult> getResultList() {
		return this.resultList;
	}

	public void addResult(MAResult result) {
		resultList.add(result);
	}

	public void removeResult(MAResult result) {
		resultList.remove(result);
	}

	public boolean isReloadable() {
		return reloadable;
	}

	public void setReloadable(boolean reloadable) {
		this.reloadable = reloadable;
	}

	public static boolean isValidJSON(final String json) {
		boolean valid = false;
		try {
			final JsonParser parser = new ObjectMapper().getFactory()
					.createParser(json);
			while (parser.nextToken() != null) {
			}
			valid = true;
		} catch (Exception e) {
			Logger.info("isValidJSON: ", e);
			valid = false;
		}
		return valid;
	}

	public List<ValidationError> validate() {
		List<ValidationError> errorList = new ArrayList<ValidationError>();
		if (this.title == null || this.title.isEmpty()) {
			errorList.add(new ValidationError("title", "Missing title"));
		}
		if (this.viewUrl == null) {
			errorList.add(new ValidationError("viewUrl", "Missing URL"));
		}
		String pathRegEx = "^(\\/\\w+)+\\.\\w+(\\?(\\w+=[\\w\\d]+(&\\w+=[\\w\\d]+)+)+)*$";
		if (!(validateUrl(this.viewUrl) || this.viewUrl.matches(pathRegEx) || this.viewUrl
				.isEmpty())) {
			errorList.add(new ValidationError("viewUrl",
					"Neither a path nor an URL (you can leave it empty)"));
		}
		if (this.jsonData == null || this.jsonData.isEmpty()) {
			errorList.add(new ValidationError("jsonData",
					"JSON data missing or invalid JSON format."));
		}
		if (this.jsonData != null && !isValidJSON(this.jsonData)) {
			errorList
					.add(new ValidationError("jsonData",
							"Problems deserializing JSON data string: invalid JSON format."));
		}
		return errorList.isEmpty() ? null : errorList;
	}

	private boolean validateUrl(String url) {
		try {
			new URL(url);
		} catch (MalformedURLException malformedURLException) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return id + " " + title;
	}

	public static String asJsonForPublic(MAComponent component)
			throws JsonProcessingException {
		// Serialize MAComponent into JSON (only the public part)
		ObjectWriter objectWriter = new ObjectMapper()
				.writerWithView(MAComponent.JsonForPublic.class);
		String componentAsJson = objectWriter.writeValueAsString(component);
		return componentAsJson;
	}

	public static MAComponent findById(Long id) {
		return JPA.em().find(MAComponent.class, id);
	}

	public static List<MAComponent> findAll() {
		TypedQuery<MAComponent> query = JPA.em().createQuery(
				"SELECT e FROM MAComponent e", MAComponent.class);
		return query.getResultList();
	}

	public static void changeComponentOrder(MAComponent component, int newIndex) {
		String queryStr = "UPDATE MAComponent SET componentList_order = "
				+ ":newIndex WHERE id = :id";
		Query query = JPA.em().createQuery(queryStr);
		query.setParameter("newIndex", newIndex);
		query.setParameter("id", component.id);
		query.executeUpdate();
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

}
