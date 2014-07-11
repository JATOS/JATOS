package models;

import java.sql.Timestamp;
import java.util.Date;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

import play.db.jpa.JPA;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
public class MAResult {

	@Id
	@GeneratedValue
	public Long id;

	/**
	 * Time and date when the component was started.
	 */
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
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "component_id")
	public MAComponent component;

	@JsonIgnore
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "worker_id")
	public MTWorker worker;

	public String result;

	public MAResult() {
	}

	public MAResult(MAComponent component, MTWorker worker) {
		this.date = new Timestamp(new Date().getTime());
		this.component = component;
		this.worker = worker;
		this.state = State.STARTED;
	}

	@Override
	public String toString() {
		return id + ", " + date + ", " + component.id + ", " + worker.workerId;
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
