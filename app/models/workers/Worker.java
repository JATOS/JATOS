package models.workers;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.AttributeOverride;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.DiscriminatorColumn;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.OrderColumn;
import javax.persistence.TypedQuery;

import models.results.StudyResult;
import play.db.jpa.JPA;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;

/**
 * Abstract domain model and DAO of a worker. Workers are doing studies (and
 * their components) and produce study results (and their component results).
 * 
 * @author Kristian Lange
 */
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = Worker.DISCRIMINATOR)
@AttributeOverride(name = Worker.DISCRIMINATOR, column = @Column(name = Worker.DISCRIMINATOR, nullable = false, insertable = false, updatable = false))
@JsonTypeInfo(use = JsonTypeInfo.Id.NONE, include = As.WRAPPER_OBJECT, property = "type")
public abstract class Worker {

	public static final String DISCRIMINATOR = "workerType";
	public static final String UI_WORKER_TYPE = "uiWorkerType";

	@Id
	@GeneratedValue
	private Long id;

	@JsonIgnore
	@OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	@OrderColumn(name = "studyResultList_order")
	@JoinColumn(name = "worker_id")
	private List<StudyResult> studyResultList = new ArrayList<StudyResult>();

	public Worker() {
	}

	public abstract String generateConfirmationCode();

	public void setId(Long id) {
		this.id = id;
	}

	public Long getId() {
		return this.id;
	}

	public abstract String getWorkerType();

	@JsonProperty("uiWorkerType")
	public abstract String getUIWorkerType();

	public void setStudyResultList(List<StudyResult> studyResultList) {
		this.studyResultList = studyResultList;
	}

	public List<StudyResult> getStudyResultList() {
		return this.studyResultList;
	}

	public void addStudyResult(StudyResult studyResult) {
		studyResultList.add(studyResult);
	}

	public void removeStudyResult(StudyResult studyResult) {
		studyResultList.remove(studyResult);
	}

	@Override
	public String toString() {
		return String.valueOf(id);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof Worker)) {
			return false;
		}
		Worker other = (Worker) obj;
		if (id == null) {
			if (other.getId() != null) {
				return false;
			}
		} else if (!id.equals(other.getId())) {
			return false;
		}
		return true;
	}

	public static Worker findById(Long id) {
		return JPA.em().find(Worker.class, id);
	}

	public static List<Worker> findAll() {
		TypedQuery<Worker> query = JPA.em().createQuery(
				"SELECT e FROM Worker e", Worker.class);
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
