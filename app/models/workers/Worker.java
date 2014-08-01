package models.workers;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.DiscriminatorColumn;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;

import models.results.StudyResult;
import play.db.jpa.JPA;

@Entity
@Inheritance
@DiscriminatorColumn(name="workerType")
public abstract class Worker {

	@Id
	@GeneratedValue
	private String id;

	@OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	@JoinColumn(name = "worker_id")
	private List<StudyResult> studyResultList = new ArrayList<StudyResult>();

	public Worker() {
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getId() {
		return this.id;
	}
	
	public void setStudyResultList(List<StudyResult> studyResultList) {
		this.studyResultList = studyResultList;
	}
	
	public List<StudyResult> getStudyResultList() {
		return this.studyResultList;
	}
	
	public void addStudyResult(StudyResult studyresult) {
		studyResultList.add(studyresult);
	}
	
	@Override
	public String toString() {
		return id;
	}

	public static Worker findById(String id) {
		return JPA.em().find(Worker.class, id);
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
