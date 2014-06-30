package models;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
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
	public String jsonData;

	@JsonView(MAComponent.Admin.class)
	@OneToMany(mappedBy = "component", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	public List<MAResult> resultList;

	public MAComponent() {
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
