package models.results;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;

import models.MAStudy;
import play.db.jpa.JPA;

import com.fasterxml.jackson.annotation.JsonFormat;

@Entity
public class StudyResult {

	@Id
	@GeneratedValue
	private Long id;

	/**
	 * Time and date when the study was started.
	 */
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd,HH:mm:ss")
	private Timestamp startDate;

	public enum StudyState {
		STARTED, FINISHED, FAIL
	};

	/**
	 * State in the progress of a study.
	 */
	private StudyState studyState;

	private boolean sandbox; // Whether this is just test, e.g. in MTurk sandbox

	@OneToOne(fetch = FetchType.LAZY)
	private MAStudy study;

	@OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	private List<ComponentResult> componentResultList = new ArrayList<ComponentResult>();

	private String confirmationCode;

	public StudyResult() {
	}

	public StudyResult(MAStudy study) {
		this.startDate = new Timestamp(new Date().getTime());
		this.study = study;
		this.studyState = StudyState.STARTED;
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

	public void setState(StudyState state) {
		this.studyState = state;
	}

	public StudyState getState() {
		return this.studyState;
	}
	
	public void setSandbox(boolean sandbox) {
		this.sandbox = sandbox;
	}
	
	public boolean getSandbox() {
		return this.sandbox;
	}

	public void setStudy(MAStudy study) {
		this.study = study;
	}

	public MAStudy getStudy() {
		return this.study;
	}

	public void setConfirmationCode(String confirmationCode) {
		this.confirmationCode = confirmationCode;
	}

	public String getConfirmationCode() {
		return this.confirmationCode;
	}

	public String generateConfirmationCode() {
		this.confirmationCode = UUID.randomUUID().toString();
		return this.confirmationCode;
	}

	public void setComponentResultList(List<ComponentResult> componentResultList) {
		this.componentResultList = componentResultList;
	}

	public List<ComponentResult> getComponentResultList() {
		return this.componentResultList;
	}

	public void addComponentResult(ComponentResult componentResult) {
		componentResultList.add(componentResult);
	}

	@Override
	public String toString() {
		return id + ", " + startDate + ", " + study.getId();
	}

	public static StudyResult findById(Long id) {
		return JPA.em().find(StudyResult.class, id);
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
