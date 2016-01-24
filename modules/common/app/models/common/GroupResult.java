package models.common;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Model and DB entity of a group result. A group result defines some properties
 * and who's member in a group of a group study.
 * 
 * Members of a GroupResult (or just group) are the StudyResults and not the
 * workers. But a studyResult is always associated with a Worker.
 * 
 * A group can be joined or left.
 * 
 * An active member is a StudyResult who joined a group and is in the
 * activeMemberList. A former member is in the historyMemberList.
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
		FINISHED, // Group study run is finished
		FIXED; // No new members can join the group
		public static String allStatesAsString() {
			String str = Arrays.toString(values());
			return str.substring(1, str.length() - 1);
		}
	}

	/**
	 * Current group result state (Yes, it should be named groupResultState -
	 * but hey, it's so much nicer this way.)
	 */
	private GroupState groupState;

	/**
	 * Temporary, global data storage that can be used via Publix API (from
	 * JavaScript in the browser) to exchange data in between a group while the
	 * study is running. All members of this group share the same
	 * groupSessionData. It will be deleted after the group is finished. It's
	 * stored as a normal string but jatos.js is converting it into JSON. We use
	 * versioning to prevent concurrent changes of the data. It's initialised
	 * with an empty JSON object.
	 */
	@Lob
	private String groupSessionData = "{}";

	/**
	 * Current version of the groupSessionData. With each change of the data it
	 * is increased by 1. We use versioning to prevent concurrent changes of the
	 * data.
	 */
	@Column(nullable = false)
	private Long groupSessionVersion = 1l;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "batch_id")
	private Batch batch;

	/**
	 * Contains all current active members of this group. Members are
	 * StudyResults. This relationship is bidirectional.
	 */
	@OneToMany(fetch = FetchType.EAGER)
	@JoinColumn(name = "activeGroupMember_id")
	private Set<StudyResult> activeMemberList = new HashSet<>();

	/**
	 * Contains all former members of this group (not active any more) that
	 * somehow finished this study. This relationship is unidirectional.
	 */
	@OneToMany(fetch = FetchType.LAZY)
	@JoinColumn(name = "historyGroupMember_id")
	private Set<StudyResult> historyMemberList = new HashSet<>();

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

	public void setGroupSessionData(String groupSessionData) {
		this.groupSessionData = groupSessionData;
	}

	public String getGroupSessionData() {
		return this.groupSessionData;
	}

	public Long getGroupSessionVersion() {
		return groupSessionVersion;
	}

	public void setGroupSessionVersion(Long groupSessionVersion) {
		this.groupSessionVersion = groupSessionVersion;
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

	public void setActiveMemberList(Set<StudyResult> activeMemberList) {
		this.activeMemberList = activeMemberList;
	}

	public Set<StudyResult> getActiveMemberList() {
		return this.activeMemberList;
	}

	public void removeActiveMember(StudyResult studyResult) {
		activeMemberList.remove(studyResult);
	}

	public void addActiveMember(StudyResult studyResult) {
		activeMemberList.add(studyResult);
	}

	public void setHistoryMemberList(Set<StudyResult> historyMemberList) {
		this.historyMemberList = historyMemberList;
	}

	public Set<StudyResult> getHistoryMemberList() {
		return this.historyMemberList;
	}

	public void removeHistoryMember(StudyResult studyResult) {
		historyMemberList.remove(studyResult);
	}

	public void addHistoryMember(StudyResult studyResult) {
		historyMemberList.add(studyResult);
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
