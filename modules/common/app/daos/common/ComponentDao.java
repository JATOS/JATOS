package daos.common;

import models.common.Component;
import models.common.Study;
import play.db.jpa.JPAApi;

import javax.inject.Inject;
import javax.inject.Singleton;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * DAO for Component entity
 */
@Singleton
public class ComponentDao extends AbstractDao {

    @Inject
    ComponentDao(JPAApi jpa) {
        super(jpa);
    }

    public void persist(Component component) {
        super.persist(component);
    }

    public Component merge(Component component) {
        return super.merge(component);
    }

    public void remove(Component component) {
        super.remove(component);
    }

    public Component findById(Long id) {
        return withReadOnlyTransaction((EntityManager em) -> em.find(Component.class, id));
    }

    /**
     * Finds the component by its ID and eagerly fetches the Study it belongs to.
     */
    public Component findByIdWithStudy(Long id) {
        return withReadOnlyTransaction((EntityManager em) -> {
            String queryStr = "SELECT c FROM Component c JOIN FETCH c.study WHERE c.id = :id";
            return em.createQuery(queryStr, Component.class)
                    .setParameter("id", id)
                    .getSingleResult();
        });
    }

    /**
     * Finds the component with this UUID
     */
    public Optional<Component> findByUuid(String uuid) {
        return withReadOnlyTransaction((EntityManager em) -> {
            String queryStr = "SELECT c FROM Component c WHERE c.uuid=:uuid";
            return em.createQuery(queryStr, Component.class)
                    .setParameter("uuid", uuid)
                    .setMaxResults(1)
                    .getResultStream()
                    .findFirst();
        });
    }

    /**
     * Searches for components with this UUID within the given study. This is faster than just searching by UUID since
     * Component does not have an index on its UUID field.
     */
    public Optional<Component> findByUuid(String uuid, Study study) {
        return withReadOnlyTransaction((EntityManager em) -> {
            String queryStr = "SELECT c FROM Component c WHERE c.study=:study AND c.uuid=:uuid";
            return em.createQuery(queryStr, Component.class)
                    .setParameter("study", study)
                    .setParameter("uuid", uuid)
                    .setMaxResults(1)
                    .getResultStream()
                    .findFirst();
        });
    }

    /**
     * Finds all components with the given title and returns them in a list. If there is none it returns an empty list.
     */
    public List<Component> findByTitle(String title) {
        return withReadOnlyTransaction((EntityManager em) -> {
            String queryStr = "SELECT c FROM Component c WHERE c.title=:title";
            TypedQuery<Component> query = em.createQuery(queryStr, Component.class);
            return query.setParameter("title", title).getResultList();
        });
    }

    /**
     * Finds the UUID of a component from the given study that already exists in another study. The study doesn't have
     * to be a managed entity.
     */
    public Optional<String> findUuidUsedByOtherStudy(Study study) {
        List<String> componentUuids = study.getComponentList().stream()
                .map(Component::getUuid)
                .collect(Collectors.toList());
        if (componentUuids.isEmpty()) return Optional.empty();

        return withReadOnlyTransaction((EntityManager em) -> em.createQuery("""
                        SELECT c.uuid FROM Component c
                        WHERE c.uuid IN :componentUuids AND c.study.uuid <> :studyUuid""",
                        String.class)
                .setParameter("componentUuids", componentUuids)
                .setParameter("studyUuid", study.getUuid())
                .setMaxResults(1)
                .getResultStream()
                .findFirst());
    }

}
