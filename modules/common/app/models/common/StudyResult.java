package models.common;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import models.common.workers.MTWorker;
import models.common.workers.Worker;
import org.apache.commons.lang3.StringUtils;

import javax.persistence.*;
import java.sql.Timestamp;
import java.util.*;

/**
 * DB entity of a study result. It's used for JSON marshalling and JPA persistance. A study result
 * essentially stores the state and the result of a study run. It has an index on id and uuid, since both are used
 * identifier.
 *
 * @author Kristian Lange
 */
@Entity
@Table(name = "StudyResult", indexes = { @Index(columnList = "uuid") })
@JsonPropertyOrder(value = { "id", "startDate", "worker", "confirmationCode", "studyState", "errorMsg", "abortMsg" })
public class StudyResult {

    @Id
    @GeneratedValue
    private Long id;

    private String uuid;

    /**
     * We don't need the whole StudyLink object and therefore we only get the ID here
     */
    @JsonIgnore
    @Column(name = "studyLink_studyCode")
    private String studyCode;

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
     * State of this study run (it actual should be called StudyResultState)
     */
    public enum StudyState {
        PRE, // Preview of study (exists only in PersonalSingleWorker and GeneralSingleWorker)
        STARTED, // Study was started
        DATA_RETRIEVED, // Study's jsonData were retrieved
        FINISHED, // Study successfully finished
        ABORTED, // Study aborted by worker
        FAIL; // Something went wrong

        public static String allStatesAsString() {
            String str = Arrays.toString(values());
            return str.substring(1, str.length() - 1);
        }
    }

    /**
     * State in the progress of a study. (Yes, it should be named studyResultState - but hey, it's so much nice this
     * way.)
     */
    private StudyState studyState;

    /**
     * Temporary, global data storage that can be used by components to exchange data while the study is running. It
     * will be deleted after the study is finished. It's stored as a normal string but jatos.js is converting it into
     * JSON. It's initialised with an empty JSON object.
     */
    @Lob
    @JsonIgnore
    private String studySessionData = "{}";

    /**
     * Study this StudyResult belongs to. This relationship is unidirectional.
     */
    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "study_id")
    private Study study;

    /**
     * Batch this StudyResult belongs to. This relationship is unidirectional.
     */
    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private Batch batch;

    /**
     * List of ComponentResults that belongs to this StudyResult. This relationship is bidirectional.
     */
    @JsonIgnore
    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn(name = "componentResultList_order")
    @JoinColumn(name = "studyResult_id")
    // Not using mappedBy because of
    // http://stackoverflow.com/questions/2956171/jpa-2-0-ordercolumn-annotation-in-hibernate-3-5
    private List<ComponentResult> componentResultList = new ArrayList<>();

    @JsonIgnore
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "worker_id", insertable = false, updatable = false, nullable = false)
    private Worker worker;

    /**
     * GroupResult this StudyResult belongs to if the corresponding study is a group study. This relationship is
     * bidirectional.
     */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activeGroupMember_id")
    private GroupResult activeGroupResult;

    /**
     * After the group study is finished the active GroupResult moves here. Before this field is null. This relationship
     * is bidirectional.
     */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "historyGroupMember_id")
    private GroupResult historyGroupResult;

    private String confirmationCode;

    /**
     * Some message usually set via jatos.finishStudy. Can be left null. Max 255 chars.
     */
    private String message;

    /**
     * Query string parameters of the URL that starts the study stored in JSON format. JATOS specific parameters are
     * removed. E.g. the URL http://localhost/start?generalsingle=123&more=foo&another=bar resolves to the parameters
     * {"more":"foo","another":"bar"}
     */
    private String urlQueryParameters;

    public StudyResult() {
    }

    public StudyResult(StudyLink studyLink, Worker worker) {
        this.uuid = UUID.randomUUID().toString();
        this.startDate = new Timestamp(new Date().getTime());
        this.studyCode = studyLink.getStudyCode();
        this.batch = studyLink.getBatch();
        this.study = batch.getStudy();
        this.worker = worker;
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

    @JsonProperty("mtWorkerId")
    public String getMtWorkerId() {
        return (worker instanceof MTWorker) ? ((MTWorker) worker).getMTWorkerId() : null;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return this.id;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getStudyCode() {
        return studyCode;
    }

    public void setStudyCode(String studyCode) {
        this.studyCode = studyCode;
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

    public void setStudyState(StudyState state) {
        this.studyState = state;
    }

    public StudyState getStudyState() {
        return this.studyState;
    }

    public void setStudySessionData(String studySessionData) {
        this.studySessionData = studySessionData;
    }

    public String getStudySessionData() {
        return this.studySessionData;
    }

    public void setStudy(Study study) {
        this.study = study;
    }

    public Study getStudy() {
        return this.study;
    }

    public void setBatch(Batch batch) {
        this.batch = batch;
    }

    public Batch getBatch() {
        return this.batch;
    }

    public void setMessage(String message) {
        this.message = StringUtils.substring(message, 0, 255);
    }

    public String getMessage() {
        return this.message;
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

    @JsonIgnore
    public Optional<ComponentResult> getFirstComponentResult() {
        if (componentResultList.size() > 0) {
            return Optional.of(componentResultList.get(0));
        } else {
            return Optional.empty();
        }
    }

    @JsonIgnore
    public Optional<ComponentResult> getLastComponentResult() {
        if (componentResultList.size() > 0) {
            return Optional.of(componentResultList.get(componentResultList.size() - 1));
        } else {
            return Optional.empty();
        }
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

    public GroupResult getActiveGroupResult() {
        return activeGroupResult;
    }

    public void setActiveGroupResult(GroupResult groupResult) {
        this.activeGroupResult = groupResult;
    }

    public GroupResult getHistoryGroupResult() {
        return historyGroupResult;
    }

    public void setHistoryGroupResult(GroupResult groupResult) {
        this.historyGroupResult = groupResult;
    }

    public String getUrlQueryParameters() {
        return urlQueryParameters;
    }

    public void setUrlQueryParameters(String urlQueryParameters) {
        this.urlQueryParameters = urlQueryParameters;
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

        if (!(obj instanceof StudyResult)) return false;

        StudyResult other = (StudyResult) obj;
        if (getId() == null) return other.getId() == null;
        return getId().equals(other.getId());
    }

}
