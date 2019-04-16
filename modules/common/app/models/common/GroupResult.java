package models.common;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.*;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * Model and DB entity of a group result. A group result defines some properties
 * and who's member in a group of a group study.
 * <p>
 * Members of a GroupResult (or just group) are the StudyResults and not the
 * workers. But a studyResult is always associated with a Worker.
 * <p>
 * A member (StudyResult) can join a group (GroupResult) or leave a group.
 * <p>
 * An active member is a StudyResult who joined a group and is in the
 * activeMemberList. A past member is in the historyMemberList.
 * <p>
 * 'Group result' and 'group' are used interchangeably.
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
     * Temporary, global data storage that can be accessed via jatos.js to
     * exchange data in between a group while the study is running. All members
     * of this group share the same groupSessionData. It will be deleted after
     * the group is finished. It's stored as a normal string but jatos.js
     * converts it into JSON. We use versioning to prevent concurrent changes of
     * the data. It's initialised with an empty JSON object.
     */
    @JsonIgnore
    @Lob
    private String groupSessionData = "{}";

    /**
     * Current version of the groupSessionData. With each change of the data it
     * is increased by 1. We use versioning to prevent concurrent changes of the
     * data.
     */
    @Column(nullable = false)
    private Long groupSessionVersion = 1L;

    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private Batch batch;

    /**
     * Contains all currently active members of this group. Members are
     * StudyResults. This relationship is bidirectional.
     */
    @JsonIgnore
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "activeGroupResult")
    private Set<StudyResult> activeMemberList = new HashSet<>();

    /**
     * Contains all past members of this group (not active any more) that
     * somehow finished this study. This relationship is bidirectional.
     */
    @JsonIgnore
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "historyGroupResult")
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
            return other.id == null;
        } else return id.equals(other.getId());
    }

}
