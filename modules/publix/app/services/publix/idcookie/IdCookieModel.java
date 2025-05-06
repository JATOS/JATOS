package services.publix.idcookie;

import controllers.publix.workers.JatosPublix.JatosRun;

import java.util.Objects;

/**
 * Model for an ID cookie. Stores several JATOS IDs that are relevant during a
 * study run, e.g. study result ID, worker ID, worker type.
 *
 * ID cookies are used to provide these IDs to jatos.js and then to the
 * component's JavaScript.
 *
 * @author Kristian Lange
 */
public class IdCookieModel {

    /**
     * Every ID cookie name starts with this String.
     */
    public static final String ID_COOKIE_NAME = "JATOS_IDS";

    /**
     * Names of the keys in the real cookie.
     */
    public static final String WORKER_ID = "workerId";
    public static final String WORKER_TYPE = "workerType";
    public static final String BATCH_ID = "batchId";
    public static final String GROUP_RESULT_ID = "groupResultId";
    public static final String STUDY_ID = "studyId";
    public static final String STUDY_RESULT_ID = "studyResultId";
    public static final String STUDY_RESULT_UUID = "studyResultUuid";
    public static final String COMPONENT_ID = "componentId";
    public static final String COMPONENT_RESULT_ID = "componentResultId";
    public static final String COMPONENT_POSITION = "componentPos";
    public static final String STUDY_ASSETS = "studyAssets";
    public static final String JATOS_RUN = "jatosRun";
    public static final String CREATION_TIME = "creationTime";
    public static final String URL_BASE_PATH = "urlBasePath";

    /**
     * Name of this ID cookie. Every name starts with {@value #ID_COOKIE_NAME}
     * and ends with '_' + the ID cookie's index.
     */
    private String name;

    /**
     * Every ID cookie has an index. It is the suffix of its name.
     */
    private int index;

    /**
     * Timestamp of when this  ID cookie was created.
     */
    private Long creationTime;

    /**
     * Name of the directory where the study's assets are stored
     */
    private String studyAssets;

    /**
     * Base path as specified in Play's config 'play.http.context'
     */
    private String urlBasePath;

    /**
     * State of a study run with a JatosWorker. If this run doesn't belong to a
     * JatosWorker this field is null. It's mainly used to distinguish between a
     * full study run and just a component run.
     */
    private JatosRun jatosRun;

    private Long workerId;
    private String workerType;
    private Long batchId;
    private Long groupResultId;
    private Long studyId;
    private Long studyResultId;
    private String studyResultUuid;
    private Long componentId;
    private Long componentResultId;
    private Integer componentPosition;

    public String getStudyAssets() {
        return studyAssets;
    }

    public void setStudyAssets(String studyAssets) {
        this.studyAssets = studyAssets;
    }

    public String getUrlBasePath() {
        return urlBasePath;
    }

    public void setUrlBasePath(String urlBasePath) {
        this.urlBasePath = urlBasePath;
    }

    public JatosRun getJatosRun() {
        return jatosRun;
    }

    public void setJatosRun(JatosRun jatosRun) {
        this.jatosRun = jatosRun;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public Long getWorkerId() {
        return workerId;
    }

    public void setWorkerId(Long workerId) {
        this.workerId = workerId;
    }

    public String getWorkerType() {
        return workerType;
    }

    public void setWorkerType(String workerType) {
        this.workerType = workerType;
    }

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    public Long getGroupResultId() {
        return groupResultId;
    }

    public void setGroupResultId(Long groupResultId) {
        this.groupResultId = groupResultId;
    }

    public Long getStudyId() {
        return studyId;
    }

    public void setStudyId(Long studyId) {
        this.studyId = studyId;
    }

    public Long getStudyResultId() {
        return studyResultId;
    }

    public void setStudyResultId(Long studyResultId) {
        this.studyResultId = studyResultId;
    }

    public String getStudyResultUuid() {
        return studyResultUuid;
    }

    public void setStudyResultUuid(String studyResultUuid) {
        this.studyResultUuid = studyResultUuid;
    }

    public Long getComponentId() {
        return componentId;
    }

    public void setComponentId(Long componentId) {
        this.componentId = componentId;
    }

    public Long getComponentResultId() {
        return componentResultId;
    }

    public void setComponentResultId(Long componentResultId) {
        this.componentResultId = componentResultId;
    }

    public Integer getComponentPosition() {
        return componentPosition;
    }

    public void setComponentPosition(Integer componentPosition) {
        this.componentPosition = componentPosition;
    }

    public Long getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(Long creationTime) {
        this.creationTime = creationTime;
    }

    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (other == null) {
            return false;
        }
        if (getClass() != other.getClass()) {
            return false;
        }
        IdCookieModel otherIdCookie = (IdCookieModel) other;
        return ((Objects.equals(name, otherIdCookie.name))
                && index == otherIdCookie.index
                && (Objects.equals(creationTime, otherIdCookie.creationTime))
                && (Objects.equals(studyAssets, otherIdCookie.studyAssets))
                && (Objects.equals(urlBasePath, otherIdCookie.urlBasePath))
                && (Objects.equals(jatosRun, otherIdCookie.jatosRun))
                && (Objects.equals(workerId, otherIdCookie.workerId))
                && (Objects.equals(workerType, otherIdCookie.workerType))
                && (Objects.equals(batchId, otherIdCookie.batchId))
                && (Objects.equals(groupResultId, otherIdCookie.groupResultId))
                && (Objects.equals(studyId, otherIdCookie.studyId))
                && (Objects.equals(studyResultId, otherIdCookie.studyResultId))
                && (Objects.equals(componentId, otherIdCookie.componentId))
                && (Objects.equals(componentResultId, otherIdCookie.componentResultId))
                && (Objects.equals(componentPosition, otherIdCookie.componentPosition)));
    }

}
