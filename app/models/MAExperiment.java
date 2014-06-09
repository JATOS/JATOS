package models;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.List;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.TypedQuery;

import play.db.jpa.JPA;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.ObjectMapper;

@Entity
public class MAExperiment {

	public static class Public {
	}

	public static class Admin extends Public {
	}

	@Id
	@GeneratedValue
	@JsonView(MAExperiment.Public.class)
	public Long id;

	@JsonView(MAExperiment.Public.class)
	public String title;

	@JsonView(MAExperiment.Admin.class)
	public Timestamp date;

	@JsonIgnore
	public String data;

	@JsonView(MAExperiment.Admin.class)
	@OneToMany(mappedBy = "experiment", fetch = FetchType.LAZY)
	public List<MAResult> resultList;

	public MAExperiment() {
	}

	public String getData() {
		ObjectMapper mapper = new ObjectMapper();
		String data = null;
		try {
			Object json = mapper.readValue(this.data, Object.class);
			data = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(
					json);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return data;
	}

	public void setData(String data) {
		try {
			ObjectMapper mapper = new ObjectMapper();
			this.data = mapper.readTree(data).toString();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public String validate() {
		if (this.title == null || this.title.isEmpty()) {
			return "Missing title";
		}
		if (this.data == null || this.data.isEmpty()) {
			return "Data missing or invalid JSON format.";
		}
		try {
			ObjectMapper mapper = new ObjectMapper();
			mapper.readTree(this.data);
		} catch (IOException e) {
			return "Problems deserializing data string: invalid JSON format.";
		}

		return null;
	}

	@Override
	public String toString() {
		return id + ", " + title + ", " + data;
	}

	public static MAExperiment findById(Long id) {
		return JPA.em().find(MAExperiment.class, id);
	}

	public static List<MAExperiment> findAll() {
		TypedQuery<MAExperiment> query = JPA.em().createQuery(
				"SELECT e FROM MAExperiment e", MAExperiment.class);
		return query.getResultList();
	}

	public MAExperiment persist() {
		JPA.em().persist(this);
		return this;
	}

	public MAExperiment merge() {
		JPA.em().merge(this);
		return this;
	}

}
