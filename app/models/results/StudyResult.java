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
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.OrderColumn;
import javax.persistence.TypedQuery;

import models.StudyModel;
import models.workers.Worker;
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
		STARTED, // Study was started
		DATA_RETRIEVED, // Study's jsonData were retrieved
		FINISHED, // Study finished
		FAIL // Something went wrong
	};

	/**
	 * State in the progress of a study.
	 */
	private StudyState studyState;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "study_id")
	private StudyModel study;

	@OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	@OrderColumn(name = "componentResultList_order")
	@JoinColumn(name = "studyResult_id")
	private List<ComponentResult> componentResultList = new ArrayList<ComponentResult>();

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "worker_id")
	private Worker worker;

	private String confirmationCode;

	public StudyResult() {
	}

	public StudyResult(StudyModel study) {
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

	public void setStartDate(Timestamp date) {
		this.startDate = date;
	}

	public Timestamp getStartDate() {
		return this.startDate;
	}

	public void setStudyState(StudyState state) {
		this.studyState = state;
	}

	public StudyState getStudyState() {
		return this.studyState;
	}

	public void setStudy(StudyModel study) {
		this.study = study;
	}

	public StudyModel getStudy() {
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

	public void removeComponentResult(ComponentResult componentResult) {
		componentResultList.remove(componentResult);
	}

	public void addComponentResult(ComponentResult componentResult) {
		componentResultList.add(componentResult);
	}

	public void setWorker(Worker worker) {
		this.worker = worker;
	}

	public Worker getWorker() {
		return this.worker;
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
		if (!(obj instanceof StudyResult)) {
			return false;
		}
		StudyResult other = (StudyResult) obj;
		if (id == null) {
			if (other.id != null) {
				return false;
			}
		} else if (!id.equals(other.getId())) {
			return false;
		}
		return true;
	}

	public static StudyResult findById(Long id) {
		return JPA.em().find(StudyResult.class, id);
	}

	public static List<StudyResult> findAllByStudy(StudyModel study) {
		String queryStr = "SELECT e FROM StudyResult e "
				+ "WHERE e.study=:studyId";
		TypedQuery<StudyResult> query = JPA.em().createQuery(queryStr,
				StudyResult.class);
		return query.setParameter("studyId", study).getResultList();
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
