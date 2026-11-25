package daos.common;

import general.common.JpaTransactions;
import jakarta.persistence.EntityManager;
import models.common.Batch;
import models.common.StudyLink;
import models.common.workers.Worker;
import models.common.workers.WorkerType;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;

@Singleton
public class StudyLinkDao extends AbstractDao {

    @Inject
    StudyLinkDao(JpaTransactions jpa) {
        super(jpa);
    }

    public StudyLink persist(StudyLink studyLink) {
        super.persist(studyLink);
        return studyLink;
    }

    public void remove(StudyLink studyLink) {
        super.remove(studyLink);
    }

    public StudyLink merge(StudyLink studyLink) {
        return super.merge(studyLink);
    }

    public StudyLink findByStudyCode(String studyCode) {
        return forReadOnly((EntityManager em) -> em.find(StudyLink.class, studyCode));
    }

    public int countAll() {
        return forReadOnly((EntityManager em) -> {
            Number result = (Number) em.createQuery("SELECT count(sl) FROM StudyLink sl").getSingleResult();
            return result != null ? result.intValue() : 0;
        });
    }

    public int countByBatchAndWorkerType(Batch batch, WorkerType workerType) {
        return forReadOnly((EntityManager em) -> {
            String queryStr = "SELECT count(sl) FROM StudyLink sl WHERE sl.batch = :batch AND sl.workerType = :workerType";
            Number result = (Number) em.createQuery(queryStr)
                    .setParameter("batch", batch)
                    .setParameter("workerType", workerType)
                    .getSingleResult();
            return result != null ? result.intValue() : 0;
        });
    }

    public List<StudyLink> findAllByBatchAndWorkerType(Batch batch, WorkerType workerType) {
        return forReadOnly((EntityManager em) -> {
            String queryStr = """
                    SELECT sl FROM StudyLink sl
                    LEFT JOIN FETCH sl.worker w
                    WHERE sl.batch = :batch AND sl.workerType = :workerType""";
            return em.createQuery(queryStr, StudyLink.class)
                    .setParameter("batch", batch)
                    .setParameter("workerType", workerType)
                    .getResultList();
        });
    }

    public Optional<StudyLink> findFirstByBatchAndWorkerType(Batch batch, WorkerType workerType) {
        return forReadOnly((EntityManager em) -> {
            String queryStr = "SELECT sr FROM StudyLink sr WHERE sr.batch =:batch AND sr.workerType = :workerType";
            return em.createQuery(queryStr, StudyLink.class)
                    .setParameter("batch", batch)
                    .setParameter("workerType", workerType)
                    .setMaxResults(1)
                    .getResultStream()
                    .findFirst();
        });
    }

    public Optional<StudyLink> findByBatchAndWorker(Batch batch, Worker worker) {
        return forReadOnly((EntityManager em) -> {
            String queryStr = "SELECT sr FROM StudyLink sr WHERE sr.batch =:batch AND sr.worker = :worker";
            return em.createQuery(queryStr, StudyLink.class)
                    .setParameter("batch", batch)
                    .setParameter("worker", worker)
                    .setMaxResults(1)
                    .getResultStream()
                    .findFirst();
        });
    }

}
