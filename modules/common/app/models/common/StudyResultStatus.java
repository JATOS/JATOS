package models.common;

import javax.persistence.*;
import java.sql.Timestamp;

/**
 * DB Entity and JSON model representing the status of a StudyResult. The status is used in JATOS status view and
 * has limited information of the StudyResult (just to give an overview).
 *
 * @author Kristian Lange
 */
@Entity
@Table(name = "StudyResult")
public class StudyResultStatus {

    @Id
    @GeneratedValue
    private Long id;

    /**
     * Time and date when the study was started on the server.
     */
    private Timestamp startDate;

    /**
     * Time and date when the study was finished on the server.
     */
    private Timestamp endDate;

    /**
     * Time and date when the study was last seen. This value is updated by the heartbeats that are sent by jatos.js
     * regularly and every update of its StudyResult (via @UpdateTimestamp).
     */
    private Timestamp lastSeenDate;

    /**
     * State in the progress of a study. (Yes, it should be named studyResultState - but hey, it's so much nice this
     * way.)
     */
    private StudyResult.StudyState studyState;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "study_id")
    private Study study;

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
