package daos.common.worker;

import daos.common.AbstractDao;
import models.common.workers.Worker;
import play.db.jpa.JPAApi;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.TypedQuery;
import java.util.List;

/**
 * DAO for abstract Worker entity
 *
 * @author Kristian Lange
 */
@Singleton
public class WorkerDao extends AbstractDao {

    @Inject
    WorkerDao(JPAApi jpa) {
        super(jpa);
    }

    public void create(Worker worker) {
        persist(worker);
    }

    public void update(Worker worker) {
        merge(worker);
    }

    public void remove(Worker worker) {
        super.remove(worker);
    }

    public Worker findById(Long id) {
        return jpa.em().find(Worker.class, id);
    }

    public List<Worker> findAll() {
        TypedQuery<Worker> query = jpa.em().createQuery("SELECT w FROM Worker w", Worker.class);
        return query.getResultList();
    }

    /**
     * Returns the number of Worker rows
     */
    public int count() {
        Number result = (Number) jpa.em().createQuery("SELECT COUNT(w) FROM Worker w").getSingleResult();
        return result.intValue();
    }

}
