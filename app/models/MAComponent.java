package models;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Query;
import javax.persistence.TypedQuery;

import play.data.validation.ValidationError;
import play.db.jpa.JPA;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;

@Entity
public class MAComponent {

	// For JSON serialization
	public static class Public {
	}

	// For JSON serialization
	public static class Admin extends Public {
	}

	@Id
	@GeneratedValue
	@JsonView(MAComponent.Public.class)
	public Long id;

	@JsonIgnore
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "experiment_id")
	public MAExperiment experiment;

	@JsonView(MAComponent.Public.class)
	public String title;

	@JsonView(MAComponent.Admin.class)
	public Timestamp date;

	@JsonView(MAComponent.Public.class)
	public String viewUrl; // URL or local path
	
	@JsonView(MAComponent.Public.class)
	public boolean reloadable;

	@JsonView(MAComponent.Public.class)
	public String jsonData;

	@JsonView(MAComponent.Admin.class)
	@OneToMany(mappedBy = "component", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	public List<MAResult> resultList = new ArrayList<MAResult>();
	
	@JsonView(MAComponent.Admin.class)
	@OneToMany(fetch = FetchType.LAZY)
	@JoinColumn(name="component_id", referencedColumnName="id")
	public Set<MTWorker> workerSet = new HashSet<MTWorker>();

	public MAComponent() {
	}
	
	public void update(String title, boolean reloadable, String viewUrl, String jsonData) {
		this.title = title;
		this.reloadable = reloadable;
		this.viewUrl = viewUrl;
		setJsonData(jsonData);
	}

	public String getJsonData() {
		ObjectMapper mapper = new ObjectMapper();
		String jsonData = null;
		try {
			Object json = mapper.readValue(this.jsonData, Object.class);
			jsonData = mapper.writerWithDefaultPrettyPrinter()
					.writeValueAsString(json);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return jsonData;
	}

	public void setJsonData(String jsonData) {
		if (!isValidJSON(jsonData)) {
			return;
		}
		try {
			ObjectMapper mapper = new ObjectMapper();
			this.jsonData = mapper.readTree(jsonData).toString();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void addWorker(MTWorker worker) {
		workerSet.add(worker);
	}

	public boolean hasWorker(MTWorker worker) {
		return workerSet.contains(worker);
	}
	
	@Column(name = "relaodable")
	public boolean isReloadable() {
		return reloadable;
	}
	
	public void addResult(MAResult result) {
		resultList.add(result);
	}

	public void removeResult(MAResult result) {
		resultList.remove(result);
	}

	public static boolean isValidJSON(final String json) {
		boolean valid = false;
		try {
			final JsonParser parser = new ObjectMapper().getFactory()
					.createParser(json);
			while (parser.nextToken() != null) {
			}
			valid = true;
		} catch (JsonParseException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
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
		return id + ", " + title + ", " + jsonData;
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
		String queryStr = "UPDATE MAComponent SET componentList_ORDER = "
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
