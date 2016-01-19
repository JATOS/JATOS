package models.common;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Model and DB entity of a group result.
 * 
 * @author Kristian Lange
 */
@Entity
@Table(name = "GroupResult")
public class GroupResult {

	@Id
	@GeneratedValue
	private Long id;

	public enum GroupState {
		STARTED, // Group study run was started
		FINISHED; // Group study run is finished
		public static String allStatesAsString() {
			String str = Arrays.toString(values());
			return str.substring(1, str.length() - 1);
		}
	}

	/**
	 * Current group result state (Yes, it should be named groupResultState -
	 * but hey, it's so much nice this way.)
	 */
	private GroupState groupState;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "batch_id")
	private Batch batch;

	/**
	 * StudyResult that this GroupResult belongs to as long as this study run is
	 * not finished. This relationship is bidirectional.
	 */
	@OneToMany(fetch = FetchType.EAGER)
	@JoinColumn(name = "groupResult_id")
	private Set<StudyResult> studyResultList = new HashSet<>();

	/**
	 * StudyResult that this GroupResult belongs to after this study run is
	 * finished. This relationship is unidirectional.
	 */
	@OneToMany(fetch = FetchType.LAZY)
	@JoinColumn(name = "groupResultHistory_id")
	private Set<StudyResult> studyResultHistory = new HashSet<>();

	/**
	 * Time and date when the study was started on the server.
	 */
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy/MM/dd HH:mm:ss")
	private Timestamp startDate;

	/**
	 * Time and date when the study was finished on the server.
	 */
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy/MM/dd HH:mm:ss")
	private Timestamp endDate;

	public GroupResult() {
	}

	/**
	 * Creates a new GroupResult and adds the given StudyResult as the first
	 * group member.
	 */
	public GroupResult(Batch batch) {
		this.batch = batch;
		this.startDate = new Timestamp(new Date().getTime());
		this.groupState = GroupState.STARTED;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public GroupState getGroupState() {
		return groupState;
	}

	public void setGroupState(GroupState groupState) {
		this.groupState = groupState;
	}

	public Batch getBatch() {
		return batch;
	}

	public void setBatch(Batch batch) {
		this.batch = batch;
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

	public void setStudyResultList(Set<StudyResult> studyResultList) {
		this.studyResultList = studyResultList;
	}

	public Set<StudyResult> getStudyResultList() {
		return this.studyResultList;
	}

	public void removeStudyResult(StudyResult studyResult) {
		studyResultList.remove(studyResult);
	}

	public void addStudyResult(StudyResult studyResult) {
		studyResultList.add(studyResult);
	}

	public void setStudyResultHistory(Set<StudyResult> studyResultHistory) {
		this.studyResultHistory = studyResultHistory;
	}

	public Set<StudyResult> getStudyResultHistory() {
		return this.studyResultHistory;
	}

	public void removeStudyResultFromHistory(StudyResult studyResult) {
		studyResultHistory.remove(studyResult);
	}

	public void addStudyResultToHistory(StudyResult studyResult) {
		studyResultHistory.add(studyResult);
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
		if (!(obj instanceof GroupResult)) {
			return false;
		}
		GroupResult other = (GroupResult) obj;
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
