package models.results;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.TypedQuery;

import models.ComponentModel;
import play.db.jpa.JPA;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

@Entity
@JsonPropertyOrder(value = { "resultId", "startDate", "studyId", "componentId",
		"componentState", "data" })
public class ComponentResult {

	@Id
	@JsonProperty("resultId")
	@GeneratedValue
	private Long id;

	/**
	 * Time and date when the component was started.
	 */
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd,HH:mm:ss")
	private Timestamp startDate;

	public enum ComponentState {
		STARTED, // Component was started
		DATA_RETRIEVED, // Component's jsonData were retrieved
		RESULTDATA_POSTED, // Result data were posted
		FINISHED, // Component was finished
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

	@Lob
	private String data;

	public ComponentResult() {
	}

	public ComponentResult(ComponentModel component) {
		this.startDate = new Timestamp(new Date().getTime());
		this.component = component;
		this.componentState = ComponentState.STARTED;
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

	public String asJson() throws JsonProcessingException {
		ObjectWriter objectWriter = new ObjectMapper().setTimeZone(
				TimeZone.getDefault()).writer();
		String resultAsJson = objectWriter.writeValueAsString(this);
		return resultAsJson;
	}

	@JsonProperty("componentId")
	private Long getComponentId() {
		return component.getId();
	}

	@JsonProperty("studyId")
	private Long getStudyId() {
		return component.getStudy().getId();
	}

	public static ComponentResult findById(Long id) {
		return JPA.em().find(ComponentResult.class, id);
	}

	public static List<ComponentResult> findAllByComponent(
			ComponentModel component) {
		String queryStr = "SELECT e FROM ComponentResult e "
				+ "WHERE e.component=:componentId";
		TypedQuery<ComponentResult> query = JPA.em().createQuery(queryStr,
				ComponentResult.class);
		return query.setParameter("componentId", component).getResultList();
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
