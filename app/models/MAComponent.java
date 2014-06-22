package models;

import java.io.IOException;
import java.sql.Timestamp;
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

import play.db.jpa.JPA;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
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
	@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn(name = "experiment_id")
	public MAExperiment experiment;

	@JsonView(MAComponent.Public.class)
	public String title;

	@JsonView(MAComponent.Admin.class)
	public Timestamp date;

	@JsonView(MAComponent.Admin.class)
	public String author;
	
	@JsonView(MAComponent.Admin.class)
	public String view;

	@JsonView(MAComponent.Public.class)
	public String data;

	@JsonView(MAComponent.Admin.class)
	@OneToMany(mappedBy = "component", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	public List<MAResult> resultList;

	public MAComponent() {
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
		if (this.view == null || this.view.isEmpty()) {
			return "Missing view";
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
		return id + ", " + title + ", " + author + ", " + view + ", " + data;
	}

	public static MAComponent findById(Long id) {
		return JPA.em().find(MAComponent.class, id);
	}

	public static List<MAComponent> findAll() {
		TypedQuery<MAComponent> query = JPA.em().createQuery(
				"SELECT e FROM MAComponent e", MAComponent.class);
		return query.getResultList();
	}

	public MAComponent persist() {
		JPA.em().persist(this);
		return this;
	}

	public MAComponent merge() {
		JPA.em().merge(this);
		return this;
	}

	public MAComponent remove() {
		JPA.em().remove(this);
		return this;
	}

}
