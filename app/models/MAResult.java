package models;

import java.sql.Timestamp;
import java.util.Date;
import java.util.TimeZone;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

import play.db.jpa.JPA;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

@JsonPropertyOrder({ "resultId", "workerId", "date", "state", "experimentId",
		"componentId", "data" })
@Entity
public class MAResult {

	@JsonProperty("resultId")
	@Id
	@GeneratedValue
	public Long id;

	/**
	 * Time and date when the component was started.
	 */
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd,HH:mm:ss")
	public Timestamp date;

	public enum State {
		STARTED, // Component was started
		DATA, // Component's jsonData were retrieved
		DONE // Component was finished
	};

	/**
	 * State in the progress of a component.
	 */
	public State state;

	@JsonIgnore
	// We get it via a getter
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "component_id")
	public MAComponent component;

	@JsonIgnore
	// We get it via a getter
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "worker_id")
	public MAWorker worker;

	public String data;

	public MAResult() {
	}

	public MAResult(MAComponent component, MAWorker worker) {
		this.date = new Timestamp(new Date().getTime());
		this.component = component;
		this.worker = worker;
		this.state = State.STARTED;
	}

	@Override
	public String toString() {
		return id + ", " + date + ", " + component.getId() + ", "
				+ worker.workerId;
	}

	public static String asJson(MAResult result) throws JsonProcessingException {
		// Serialize MAResult into JSON
		ObjectWriter objectWriter = new ObjectMapper().setTimeZone(
				TimeZone.getDefault()).writer();
		String resultAsJson = objectWriter.writeValueAsString(result);
		return resultAsJson;
	}

	@JsonProperty("componentId")
	private Long getComponentId() {
		if (component != null) {
			return component.getId();
		} else {
			return null;
		}
	}

	@JsonProperty("workerId")
	private String getWorkerId() {
		if (worker != null) {
			return worker.workerId;
		} else {
			return null;
		}
	}

	@JsonProperty("experimentId")
	private Long getExperimentId() {
		if (component != null && component.getExperiment() != null) {
			return component.getExperiment().getId();
		} else {
			return null;
		}
	}

	public static MAResult findById(Long id) {
		return JPA.em().find(MAResult.class, id);
	}

	public void persist() {
		JPA.em().persist(this);
	}

	public void remove() {
		JPA.em().remove(this);
	}

	public void merge() {
		JPA.em().merge(this);
	}

}
