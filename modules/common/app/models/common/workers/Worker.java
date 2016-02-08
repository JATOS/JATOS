package models.common.workers;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.AttributeOverride;
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
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;

import models.common.StudyResult;
import play.data.validation.ValidationError;

/**
 * Abstract domain model / entity of a worker. It's used fro JSON marshaling and
 * JPA persistance.
 * 
 * Workers are doing studies (and their components) and produce study results
 * (and their component results).
 * 
 * All worker entities are stored in the same database table. Inheritance is
 * established with an discriminator column.
 * 
 * @author Kristian Lange (2015)
 */
@Entity
@Table(name = "Worker")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = Worker.DISCRIMINATOR)
@AttributeOverride(name = Worker.DISCRIMINATOR, column = @Column(name = Worker.DISCRIMINATOR, nullable = false, insertable = false, updatable = false) )
@JsonTypeInfo(use = JsonTypeInfo.Id.NONE, include = As.WRAPPER_OBJECT, property = "type")
public abstract class Worker {

	public static final String DISCRIMINATOR = "workerType";
	public static final String UI_WORKER_TYPE = "uiWorkerType";

	@Id
	@GeneratedValue
	private Long id;

	/**
	 * Ordered list of StudyResults this worker has produced while running
	 * studies. This relationship is bidirectional.
	 */
	@JsonIgnore
	@OneToMany(fetch = FetchType.LAZY)
	@OrderColumn(name = "studyResultList_order")
	@JoinColumn(name = "worker_id")
	// Not using mappedBy because of
	// http://stackoverflow.com/questions/2956171/jpa-2-0-ordercolumn-annotation-in-hibernate-3-5
	private List<StudyResult> studyResultList = new ArrayList<>();

	public Worker() {
	}

	public abstract String generateConfirmationCode();

	public abstract List<ValidationError> validate();

	public abstract String getWorkerType();

	@JsonProperty("uiWorkerType")
	public abstract String getUIWorkerType();

	/**
	 * Little helper method that translates a workerType into the UI worker
	 * type.
	 */
	@JsonIgnore
	public static String getUIWorkerType(String workerType) {
		switch (workerType) {
		case JatosWorker.WORKER_TYPE:
			return JatosWorker.UI_WORKER_TYPE;
		case GeneralSingleWorker.WORKER_TYPE:
			return GeneralSingleWorker.UI_WORKER_TYPE;
		case MTSandboxWorker.WORKER_TYPE:
			return MTSandboxWorker.UI_WORKER_TYPE;
		case MTWorker.WORKER_TYPE:
			return MTWorker.UI_WORKER_TYPE;
		case PersonalMultipleWorker.WORKER_TYPE:
			return PersonalMultipleWorker.UI_WORKER_TYPE;
		case PersonalSingleWorker.WORKER_TYPE:
			return PersonalSingleWorker.UI_WORKER_TYPE;
		default:
			return "Unknown";
		}
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getId() {
		return this.id;
	}

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
		return getWorkerType() + ":" + String.valueOf(id);
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

}
