package models.common.workers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import models.common.Batch;
import models.common.StudyResult;
import play.data.validation.ValidationError;

import javax.persistence.*;
import java.util.*;

/**
 * Abstract DB entity of a worker. It's used for JSON marshaling and JPA persistence.
 *
 * Workers are doing studies (and their components) and produce study results
 * (and their component results).
 *
 * All worker entities are stored in the same database table. Inheritance is
 * established with an discriminator column.
 *
 * @author Kristian Lange (2015)
 */
@Entity
@Table(name = "Worker")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = Worker.DISCRIMINATOR)
@AttributeOverride(name = Worker.DISCRIMINATOR,
        column = @Column(name = Worker.DISCRIMINATOR, nullable = false, insertable = false,
                updatable = false))
@JsonTypeInfo(use = JsonTypeInfo.Id.NONE, include = As.WRAPPER_OBJECT, property = "type")
public abstract class Worker {

    public static final String DISCRIMINATOR = "workerType";
    public static final String UI_WORKER_TYPE = "uiWorkerType";

    @Id
    @GeneratedValue
    private Long id;

    /**
     * Ordered list of StudyResults this worker has produced while running
     * studies. This relationship is bidirectional.
     */
    @JsonIgnore
    @OneToMany(fetch = FetchType.LAZY)
    @OrderColumn(name = "studyResultList_order")
    @JoinColumn(name = "worker_id")
    // Not using mappedBy because of
    // http://stackoverflow.com/questions/2956171/jpa-2-0-ordercolumn-annotation-in-hibernate-3-5
    private List<StudyResult> studyResultList = new ArrayList<>();

    /**
     * List of batches this worker belongs to. This relationship is
     * bidirectional.
     */
    @ManyToMany(mappedBy = "workerList", fetch = FetchType.LAZY)
    private Set<Batch> batchList = new HashSet<>();

    public Worker() {
    }

    public abstract String generateConfirmationCode();

    public abstract List<ValidationError> validate();

    public abstract String getWorkerType();

    @JsonProperty("uiWorkerType")
    public abstract String getUIWorkerType();

    /**
     * Little helper method that translates a workerType into the UI worker
     * type.
     */
    @JsonIgnore
    public static String getUIWorkerType(String workerType) {
        switch (workerType) {
            case JatosWorker.WORKER_TYPE:
                return JatosWorker.UI_WORKER_TYPE;
            case PersonalSingleWorker.WORKER_TYPE:
                return PersonalSingleWorker.UI_WORKER_TYPE;
            case PersonalMultipleWorker.WORKER_TYPE:
                return PersonalMultipleWorker.UI_WORKER_TYPE;
            case GeneralSingleWorker.WORKER_TYPE:
                return GeneralSingleWorker.UI_WORKER_TYPE;
            case GeneralMultipleWorker.WORKER_TYPE:
                return GeneralMultipleWorker.UI_WORKER_TYPE;
            case MTSandboxWorker.WORKER_TYPE:
                return MTSandboxWorker.UI_WORKER_TYPE;
            case MTWorker.WORKER_TYPE:
                return MTWorker.UI_WORKER_TYPE;
            default:
                return "Unknown";
        }
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return this.id;
    }

    public void setStudyResultList(List<StudyResult> studyResultList) {
        this.studyResultList = studyResultList;
    }

    public List<StudyResult> getStudyResultList() {
        return this.studyResultList;
    }

    @JsonIgnore
    public Optional<StudyResult> getFirstStudyResult() {
        return !studyResultList.isEmpty()
                ? Optional.of(studyResultList.get(0)) : Optional.empty();
    }

    @JsonIgnore
    public Optional<StudyResult> getLastStudyResult() {
        return !studyResultList.isEmpty()
                ? Optional.of(studyResultList.get(studyResultList.size() - 1)) : Optional.empty();
    }

    public void addStudyResult(StudyResult studyResult) {
        studyResultList.add(studyResult);
    }

    public void removeStudyResult(StudyResult studyResult) {
        studyResultList.remove(studyResult);
    }

    public Set<Batch> getBatchList() {
        return batchList;
    }

    public void setBatchList(Set<Batch> batchList) {
        this.batchList = batchList;
    }

    public boolean hasBatch(Batch batch) {
        return batchList.contains(batch);
    }

    public void addBatch(Batch batch) {
        batchList.add(batch);
    }

    public void removeBatch(Batch batch) {
        batchList.remove(batch);
    }

    @Override
    public String toString() {
        return getWorkerType() + ":" + id;
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

        if (!(obj instanceof Worker)) return false;

        Worker other = (Worker) obj;
        if (getId() == null) return other.getId() == null;
        return getId().equals(other.getId());
    }

}
