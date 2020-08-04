package models.common;

import models.common.workers.Worker;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.util.Objects;
import java.util.UUID;

/**
 * DB entity of a study run.
 *
 * @author Kristian Lange
 */
@Entity
@Table(name = "StudyRun")
public class StudyRun {

    @Id
    @GeneratedValue
    @Type(type="uuid-char")
    private UUID uuid;

    /**
     * Batch this study run belongs to
     */
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "batch_id")
    private Batch batch;

    /**
     * Worker is only set if one of the 'personal' workers (Personal Single or Personal Multiple)
     */
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "worker_id")
    private Worker worker;

    /**
     * Always set, but especially important for all 'general' workers (General Single, General Multiple, MTurk) and the
     * Jatos worker since they don't set the worker.
     */
    private String workerType;

    public StudyRun() {
    }

    public StudyRun(Batch batch, Worker worker) {
        this.worker = worker;
        this.workerType = worker.getWorkerType();
        this.batch = batch;
    }

    public StudyRun(Batch batch, String workerType) {
        this.workerType = workerType;
        this.batch = batch;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public Batch getBatch() {
        return batch;
    }

    public void setBatch(Batch batch) {
        this.batch = batch;
    }

    public Worker getWorker() {
        return worker;
    }

    public void setWorker(Worker worker) {
        this.worker = worker;
    }

    public String getWorkerType() {
        return workerType;
    }

    public void setWorkerType(String workerType) {
        this.workerType = workerType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StudyRun studyRun = (StudyRun) o;

        return Objects.equals(uuid, studyRun.uuid);
    }

    @Override
    public int hashCode() {
        return uuid != null ? uuid.hashCode() : 0;
    }

}
