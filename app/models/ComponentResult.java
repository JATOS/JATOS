package models;

import java.sql.Timestamp;
import java.util.Date;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;

import utils.DateUtils;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Domain model of a component result.
 * 
 * @author Kristian Lange
 */
@Entity
@JsonPropertyOrder(value = { "id", "startDate", "workerId", "workerType",
		"componentState" })
public class ComponentResult {

	public static final String COMPONENT_RESULTS = "componentResults";

	@Id
	@GeneratedValue
	private Long id;

	/**
	 * Time and date when the component was started on the server.
	 */
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy/MM/dd HH:mm:ss")
	private Timestamp startDate;

	/**
	 * Time and date when the component was finished on the server.
	 */
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy/MM/dd HH:mm:ss")
	private Timestamp endDate;

	public enum ComponentState {
		STARTED, // Component was started
		DATA_RETRIEVED, // Component's jsonData were retrieved
		RESULTDATA_POSTED, // Result data were posted
		FINISHED, // Component was finished
		RELOADED, // Component was reloaded
		ABORTED, // Component aborted by worker
		FAIL // Something went wrong
	};

	/**
	 * State in the progress of a component.
	 */
	private ComponentState componentState;

	@JsonIgnore
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "component_id")
	private ComponentModel component;

	@JsonIgnore
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "studyResult_id")
	private StudyResult studyResult;

	/**
	 * Result data string submitted from the client during running the
	 * component. It can be any string and doesn't have to be in JSON format.
	 */
	@Lob
	@JsonIgnore
	private String data;

	/**
	 * Error message in case something went wrong with the component (state is
	 * FAIL). Can be left null.
	 */
	private String errorMsg;

	public ComponentResult() {
	}

	public ComponentResult(ComponentModel component) {
		this.startDate = new Timestamp(new Date().getTime());
		this.component = component;
		this.componentState = ComponentState.STARTED;
	}

	@JsonProperty("workerId")
	public Long getWorkerId() {
		return studyResult.getWorker().getId();
	}

	@JsonProperty("workerType")
	public String getWorkerType() {
		return studyResult.getWorker().getWorkerType();
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getId() {
		return this.id;
	}

	public void setStartDate(Timestamp startDate) {
		this.startDate = startDate;
	}

	public Timestamp getStartDate() {
		return this.startDate;
	}

	public void setEndDate(Timestamp endDate) {
		this.endDate = endDate;
	}

	public Timestamp getEndDate() {
		return this.endDate;
	}

	public String getDuration() {
		return DateUtils.getDurationPretty(startDate, endDate);
	}

	public void setComponentState(ComponentState state) {
		this.componentState = state;
	}

	public ComponentState getComponentState() {
		return this.componentState;
	}

	public void setComponent(ComponentModel component) {
		this.component = component;
	}

	public ComponentModel getComponent() {
		return this.component;
	}

	public void setData(String data) {
		this.data = data;
	}

	public String getData() {
		return this.data;
	}

	public void setErrorMsg(String errorMsg) {
		this.errorMsg = errorMsg;
	}

	public String getErrorMsg() {
		return this.errorMsg;
	}

	public void setStudyResult(StudyResult studyResult) {
		this.studyResult = studyResult;
	}

	public StudyResult getStudyResult() {
		return this.studyResult;
	}

	@Override
	public String toString() {
		return id + ", " + startDate + ", " + component.getId();
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
		if (!(obj instanceof ComponentResult)) {
			return false;
		}
		ComponentResult other = (ComponentResult) obj;
		if (id == null) {
			if (other.id != null) {
				return false;
			}
		} else if (!id.equals(other.getId())) {
			return false;
		}
		return true;
	}

}