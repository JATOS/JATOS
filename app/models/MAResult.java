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
import play.db.jpa.Transactional;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
public class MAResult {

	@Id
	@GeneratedValue
	public Long id;
	
	public Timestamp date;
	
	@JsonIgnore
	@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn(name = "experiment_id")
	public MAExperiment experiment;
	
	public String result;
	
	public MAResult() {
	}
	
	public MAResult(String result, Long experimentId) {
		this.date = new Timestamp(new Date().getTime());
		this.result = result;
		MAExperiment experiment = MAExperiment.findById(experimentId);
		this.experiment = experiment;
	}
	
	@Override
	public String toString() {
		return id + ", " + experiment.id + ", " + date + ", " + result;
	}
	
	public String validate() {
		if (this.experiment == null || this.date == null
				|| this.result == null) {
			return "Result not valid";
		}
		return null;
	}
	
	@Transactional
	public static MAResult findById(Long id) {
		return JPA.em().find(MAResult.class, id);
	}
	
	@Transactional
	public MAResult persist() {
		JPA.em().persist(this);
		return this;
	}

}
