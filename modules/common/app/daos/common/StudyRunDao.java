package daos.common;

import models.common.Batch;
import models.common.StudyRun;
import models.common.workers.Worker;
import play.db.jpa.JPAApi;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @author Kristian Lange
 */
@Singleton
public class StudyRunDao extends AbstractDao {

    @Inject
    StudyRunDao(JPAApi jpa) {
        super(jpa);
    }

    public StudyRun create(StudyRun studyRun) {
        persist(studyRun);
        return studyRun;
    }

    public void remove(StudyRun studyRun) {
        super.remove(studyRun);
    }

    public void update(StudyRun studyRun) {
        merge(studyRun);
    }

    public StudyRun findByUuid(UUID uuid) {
        return jpa.em().find(StudyRun.class, uuid);
    }

    public Optional<StudyRun> findByBatchAndWorkerType(Batch batch, String workerType) {
        String queryStr = "SELECT sr FROM StudyRun sr WHERE sr.batch =:batch AND sr.workerType = :workerType";
        List<StudyRun> studyRun = jpa.em().createQuery(queryStr, StudyRun.class)
                .setParameter("batch", batch)
                .setParameter("workerType", workerType)
                .setMaxResults(1)
                .getResultList();
        return !studyRun.isEmpty() ? Optional.of(studyRun.get(0)) : Optional.empty();
    }

    public Optional<StudyRun> findByBatchAndWorker(Batch batch, Worker worker) {
        String queryStr = "SELECT sr FROM StudyRun sr WHERE sr.batch =:batch AND sr.worker = :worker";
        List<StudyRun> studyRun = jpa.em().createQuery(queryStr, StudyRun.class)
                .setParameter("batch", batch)
                .setParameter("worker", worker)
                .setMaxResults(1)
                .getResultList();
        return !studyRun.isEmpty() ? Optional.of(studyRun.get(0)) : Optional.empty();
    }

    public int removeAllByBatch(Batch batch) {
        return jpa.em().createQuery("DELETE FROM StudyRun sr WHERE sr.batch = :batch")
                .setParameter("batch", batch)
                .executeUpdate();
    }

}
