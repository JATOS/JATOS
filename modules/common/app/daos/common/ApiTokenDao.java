package daos.common;

import models.common.ApiToken;
import models.common.User;
import play.db.jpa.JPAApi;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;

/**
 * DAO for ApiToken entity
 *
 * @author Kristian Lange
 */
@Singleton
public class ApiTokenDao extends AbstractDao {

    @Inject
    ApiTokenDao(JPAApi jpa) {
        super(jpa);
    }

    public void create(ApiToken apiToken) {
        persist(apiToken);
    }

    public void update(ApiToken apiToken) {
        merge(apiToken);
    }

    public void remove(ApiToken apiToken) {
        super.remove(apiToken);
    }

    public ApiToken find(Long id) {
        return jpa.withTransaction(em -> {
            return em.find(ApiToken.class, id);
        });
    }

    public Optional<ApiToken> findByHash(String tokenHash) {
        return jpa.withTransaction(em -> {
            String queryStr = "SELECT t FROM ApiToken t " +
                    "LEFT JOIN FETCH t.user u " +
                    "LEFT JOIN FETCH u.studyList " +
                    "WHERE t.tokenHash = :tokenHash";
            List<ApiToken> apiToken = em.createQuery(queryStr, ApiToken.class)
                    .setParameter("tokenHash", tokenHash)
                    .getResultList();
            return !apiToken.isEmpty() ? Optional.of(apiToken.get(0)) : Optional.empty();
        });
    }

    public List<ApiToken> findByUser(User user) {
        String queryStr = "SELECT t FROM ApiToken t WHERE t.user = :user";
        return jpa.withTransaction(em -> {
            return em.createQuery(queryStr, ApiToken.class)
                    .setParameter("user", user)
                    .getResultList();
        });
    }

}
