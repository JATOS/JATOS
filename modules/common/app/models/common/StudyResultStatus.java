package models.common;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.persistence.*;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Hibernate Entity and JSON model representing the status of a StudyResult. The status is used in JATOS status view and
 * has limited information of the StudyResult (just to give an overview).
 *
 * @author Kristian Lange
 */
@Entity
@Table(name = "StudyResult")
@JsonPropertyOrder(value = { "id", "startDate", "lastSeenDate", "studyState", "userList" })
public class StudyResultStatus {

    @Id
    @GeneratedValue
    @JsonIgnore
    private Long id;

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

    /**
     * Time and date when the study was last seen (server time). jatos.js sends a periodic heart beat and the time of
     * the last one is saved here.
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy/MM/dd HH:mm:ss")
    private Timestamp lastSeenDate;

    /**
     * State in the progress of a study. (Yes, it should be named studyResultState - but hey, it's so much nice this
     * way.)
     */
    private StudyResult.StudyState studyState;

    @JsonIgnore
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "study_id")
    private Study study;

    /**
     * List of user Strings containing the User's name and username of all Users that are members of the Study that this
     * StudyResult belongs to.
     */
    @Transient
    private List<String> users = new ArrayList<>();

    public StudyResultStatus() {
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

    public Timestamp getLastSeenDate() {
        return lastSeenDate;
    }

    public void setLastSeenDate(Timestamp lastSeenDate) {
        this.lastSeenDate = lastSeenDate;
    }

    public void setStudyState(StudyResult.StudyState state) {
        this.studyState = state;
    }

    public StudyResult.StudyState getStudyState() {
        return this.studyState;
    }

    public Study getStudy() {
        return study;
    }

    public void setStudy(Study study) {
        this.study = study;
    }

    public void setUsers(List<String> users) {
        this.users = users;
    }

    public void addUser(String user) {
        this.users.add(user);
    }

    @JsonGetter("users")
    public List<String> getUsers() {
        return users;
    }

    @Override
    public String toString() {
        return String.valueOf(id);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getId() == null) ? 0 : getId().hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;

        if (obj == null) return false;

        if (!(obj instanceof StudyResultStatus)) return false;

        StudyResultStatus other = (StudyResultStatus) obj;
        if (getId() == null) return other.getId() == null;
        return getId().equals(other.getId());
    }

}
