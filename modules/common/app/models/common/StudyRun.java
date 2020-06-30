package models.common;

import models.common.workers.Worker;

import javax.persistence.*;
import java.util.UUID;

/**
 * Domain entity of a study run.
 *
 * @author Kristian Lange
 */
@Entity
@Table(name = "StudyRun")
public class StudyRun {

    @Id
    @GeneratedValue
    private UUID uuid;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "worker_id", insertable = false, updatable = false, nullable = false)
    private Worker worker;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "batch_id")
    private Batch batch;

    public StudyRun() {
    }

}
