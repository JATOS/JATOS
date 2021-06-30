package models.common;

import models.common.workers.Worker;
import org.apache.commons.lang3.RandomStringUtils;

import javax.persistence.*;
import java.util.Objects;

/**
 * DB entity of a study run.
 *
 * @author Kristian Lange
 */
@Entity
@Table(name = "StudyLink")
public class StudyLink {

    @Id
    private String id;

    /**
     * The Batch this study link belongs to
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

    /**
     * Only an active study link can be used to run a study
     */
    private boolean active = true;

    public StudyLink() {
    }

    public StudyLink(Batch batch, Worker worker) {
        this(batch, worker.getWorkerType());
        this.worker = worker;
    }

    public StudyLink(Batch batch, String workerType) {
        this.id = RandomStringUtils.randomAlphanumeric(11);
        this.workerType = workerType;
        this.batch = batch;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StudyLink studyLink = (StudyLink) o;

        return Objects.equals(id, studyLink.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

}