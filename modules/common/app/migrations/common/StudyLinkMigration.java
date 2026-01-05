package migrations.common;

import daos.common.StudyLinkDao;
import daos.common.worker.WorkerDao;
import daos.common.worker.WorkerType;
import models.common.Batch;
import models.common.StudyLink;
import models.common.workers.Worker;
import play.Logger;
import play.db.jpa.JPAApi;

import javax.inject.Inject;
import java.util.List;

/**
 * Migrates the database for all <3.7.1. It creates for each existing PersonalSingleWorker and PersonalMultipleWorker
 * a StudyLink.
 */
public class StudyLinkMigration {

    private static final Logger.ALogger LOGGER = Logger.of(StudyLinkMigration.class);

    private final JatosMigrations jatosMigrations;
    private final JPAApi jpa;
    private final StudyLinkDao studyLinkDao;
    private final WorkerDao workerDao;

    @Inject
    StudyLinkMigration(JatosMigrations jatosMigrations, JPAApi jpa, StudyLinkDao studyLinkDao, WorkerDao workerDao) {
        this.jatosMigrations = jatosMigrations;
        this.jpa = jpa;
        this.studyLinkDao = studyLinkDao;
        this.workerDao = workerDao;
    }

    public void run() {
        try {
            jatosMigrations.start(this::createStudyLinksForExistingPersonalWorkers);
        } catch (Exception e) {
            throw new RuntimeException("StudyLink Migration failed", e);
        }
    }

    private void createStudyLinksForExistingPersonalWorkers() {
        jpa.withTransaction((em) -> {
            if (studyLinkDao.countAll() != 0) return;

            int studyLinkCounter = 0;
            List<Worker> allWorkers = workerDao.findAll();
            for (Worker worker : allWorkers) {
                if (worker.getWorkerType() == WorkerType.PERSONAL_SINGLE
                        || worker.getWorkerType() == WorkerType.PERSONAL_MULTIPLE) {
                    for (Batch batch : worker.getBatchList()) {
                        if (studyLinkDao.findByBatchAndWorker(batch, worker).isPresent()) continue;
                        StudyLink studyLink = new StudyLink(batch, worker);
                        studyLinkDao.persist(studyLink);
                        studyLinkCounter++;
                        LOGGER.info("Created study link " + studyLinkCounter);
                    }
                }
            }
            if (studyLinkCounter > 0) LOGGER.info("Created " + studyLinkCounter + " study links for existing workers");
        });
    }

}
