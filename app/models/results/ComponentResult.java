package models.results;

import java.sql.Timestamp;
import java.util.Date;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.OneToOne;

import models.MAComponent;
import play.db.jpa.JPA;

import com.fasterxml.jackson.annotation.JsonFormat;

@Entity
public class ComponentResult {

	@Id
	@GeneratedValue
	private Long id;

	/**
	 * Time and date when the component was started.
	 */
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd,HH:mm:ss")
	private Timestamp startDate;

	public enum ComponentState {
		NEW,				// Not yet started
		STARTED,			// Component was started
		DATA_RETRIEVED,		// Component's jsonData were retrieved
		RESULTDATA_POSTED,	// Result data were posted
		FINISHED,			// Component was finished
		FAIL				// Something went wrong
	};

	/**
	 * State in the progress of a component.
	 */
	private ComponentState componetState;

	@OneToOne(fetch = FetchType.LAZY)
	private MAComponent component;

	@Lob
	private String data;

	public ComponentResult() {
	}

	public ComponentResult(MAComponent component) {
		this.startDate = new Timestamp(new Date().getTime());
		this.component = component;
		this.componetState = ComponentState.NEW;
	}
	
	public void setId(Long id) {
		this.id = id;
	}
	
	public Long getId() {
		return this.id;
	}
	
	public void setDate(Timestamp date) {
		this.startDate = date;
	}
	
	public Timestamp getDate() {
		return this.startDate;
	}
	
	public void setState(ComponentState state) {
		this.componetState = state;
	}
	
	public ComponentState getState() {
		return this.componetState;
	}
	
	public void setComponent(MAComponent component) {
		this.component = component;
	}
	
	public MAComponent getComponent() {
		return this.component;
	}
	
	public void setData(String data) {
		this.data = data;
	}
	
	public String getData() {
		return this.data;
	}

	@Override
	public String toString() {
		return id + ", " + startDate + ", " + component.getId();
	}

	public static ComponentResult findById(Long id) {
		return JPA.em().find(ComponentResult.class, id);
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
