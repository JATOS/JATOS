package models.common.workers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import daos.common.worker.WorkerType;
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
        column = @Column(name = Worker.DISCRIMINATOR, nullable = false, insertable = false, updatable = false))
@JsonTypeInfo(use = JsonTypeInfo.Id.NONE, include = As.WRAPPER_OBJECT, property = "type")
public abstract class Worker {

    public static final String DISCRIMINATOR = "workerType";
    public static final String UI_WORKER_TYPE = "uiWorkerType";
    public static final String COMMENT = "comment";

    @Id
    @GeneratedValue
    private Long id;

    @Convert(converter = WorkerTypeConverter.class)
    @Column(name = "workerType")
    private WorkerType workerType;

    /**
     * Some comment the user can give during study link/worker creation (only for {@link PersonalSingleWorker} and
     * {@link PersonalMultipleWorker}.
     */
    protected String comment;

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
    @SuppressWarnings({"FieldMayBeFinal", "MismatchedQueryAndUpdateOfCollection"})
    private List<StudyResult> studyResultList = new ArrayList<>();

    /**
     * List of batches this worker belongs to. This relationship is
     * bidirectional.
     */
    @ManyToMany(mappedBy = "workerList", fetch = FetchType.LAZY)
    @SuppressWarnings("FieldMayBeFinal")
    private Set<Batch> batchList = new HashSet<>();

    public Worker() {
    }

    public abstract String generateConfirmationCode();

    public abstract List<ValidationError> validate();

    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return this.id;
    }

    public void setWorkerType(WorkerType workerType) {
        this.workerType = workerType;
    }

    public WorkerType getWorkerType() {
        return workerType;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getComment() {
        return this.comment;
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

    public boolean hasBatch(Batch batch) {
        return batchList.contains(batch);
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
