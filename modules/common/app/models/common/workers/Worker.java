package models.common.workers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import models.common.Batch;
import models.common.StudyResult;
import play.data.validation.ValidationError;

import jakarta.persistence.*;
import java.util.*;

import static jakarta.persistence.GenerationType.IDENTITY;

/**
 * Abstract DB entity of a worker. It's used for JSON marshaling and JPA persistence.
 *
 * Workers are doing studies (and their components) and produce study results
 * (and their component results).
 *
 * All worker entities are stored in the same database table. Inheritance is
 * established with a discriminator column.
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
    public static final String COMMENT = "comment";

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    /**
     * Worker type is determined by the concrete Worker subclass / JPA discriminator.
     */
    @Convert(converter = WorkerTypeConverter.class)
    @Column(name = DISCRIMINATOR, nullable = false, insertable = false, updatable = false)
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

    public WorkerType getWorkerType() {
        return this.workerType;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getComment() {
        return this.comment;
    }

    public List<StudyResult> getStudyResultList() {
        return studyResultList;
    }

    public void addStudyResult(StudyResult studyResult) {
        studyResultList.add(studyResult);
    }

    public Set<Batch> getBatchList() {
        return batchList;
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
        return Worker.class.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Worker other)) return false;
        return getId() != null && getId().equals(other.getId());
    }

}
