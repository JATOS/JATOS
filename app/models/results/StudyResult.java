package models.results;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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
import javax.persistence.Query;
import javax.persistence.TypedQuery;

import models.StudyModel;
import models.workers.Worker;
import play.db.jpa.JPA;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Domain model and DAO of a study result.
 * 
 * @author Kristian Lange
 */
@Entity
@JsonPropertyOrder(value = { "id", "startDate", "workerId", "workerType",
		"confirmationCode", "studyState", "errorMsg", "abortMsg" })
public class StudyResult {

	@Id
	@GeneratedValue
	private Long id;

	/**
	 * Time and date when the study was started.
	 */
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy/MM/dd HH:mm:ss")
	private Timestamp startDate;

	public enum StudyState {
		STARTED, // Study was started
		DATA_RETRIEVED, // Study's jsonData were retrieved
		FINISHED, // Study successfully finished
		ABORTED, // Study aborted by worker
		FAIL // Something went wrong
	};

	/**
	 * State in the progress of a study.
	 */
	private StudyState studyState;

	@JsonIgnore
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "study_id")
	private StudyModel study;

	@JsonIgnore
	@OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	@OrderColumn(name = "componentResultList_order")
	@JoinColumn(name = "studyResult_id")
	private List<ComponentResult> componentResultList = new ArrayList<ComponentResult>();

	@JsonIgnore
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "worker_id")
	private Worker worker;

	private String confirmationCode;

	/**
	 * Error message in case something went wrong with the study (state is
	 * FAIL). Can be left null.
	 */
	private String errorMsg;

	/**
	 * Message in case the study was aborted (state is ABORTED). Can be left
	 * null.
	 */
	private String abortMsg;

	public StudyResult() {
	}

	public StudyResult(StudyModel study) {
		this.startDate = new Timestamp(new Date().getTime());
		this.study = study;
		this.studyState = StudyState.STARTED;
	}

	@JsonProperty("workerId")
	public Long getWorkerId() {
		return worker.getId();
	}

	@JsonProperty("workerType")
	public String getWorkerType() {
		return worker.getWorkerType();
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

	public void setErrorMsg(String errorMsg) {
		this.errorMsg = errorMsg;
	}

	public String getErrorMsg() {
		return this.errorMsg;
	}

	public void setAbortMsg(String abortMsg) {
		this.abortMsg = abortMsg;
	}

	public String getAbortMsg() {
		return this.abortMsg;
	}

	public void setConfirmationCode(String confirmationCode) {
		this.confirmationCode = confirmationCode;
	}

	public String getConfirmationCode() {
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

	public static List<StudyResult> findAll() {
		String queryStr = "SELECT e FROM StudyResult e";
		TypedQuery<StudyResult> query = JPA.em().createQuery(queryStr,
				StudyResult.class);
		return query.getResultList();
	}

	public static int countByStudy(StudyModel study) {
		String queryStr = "SELECT COUNT(e) FROM StudyResult e WHERE e.study=:studyId";
		Query query = JPA.em().createQuery(queryStr);
		Number result = (Number) query.setParameter("studyId", study)
				.getSingleResult();
		return result.intValue();
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
