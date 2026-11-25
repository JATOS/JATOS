package daos.common;

import general.common.JpaTransactions;
import jakarta.persistence.EntityManager;
import models.common.ApiToken;
import models.common.User;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;

/**
 * DAO for ApiToken entity
 */
@Singleton
public class ApiTokenDao extends AbstractDao {

    @Inject
    ApiTokenDao(JpaTransactions jpa) {
        super(jpa);
    }

    public void persist(ApiToken apiToken) {
        super.persist(apiToken);
    }

    public ApiToken merge(ApiToken apiToken) {
        return super.merge(apiToken);
    }

    public void remove(ApiToken apiToken) {
        super.remove(apiToken);
    }

    public ApiToken find(Long id) {
        return forReadOnly((EntityManager em) -> em.find(ApiToken.class, id));
    }

    public Optional<ApiToken> findByHash(String tokenHash) {
        return forReadOnly(em -> {
            String queryStr = """
                    SELECT t FROM ApiToken t
                    LEFT JOIN FETCH t.user u
                    LEFT JOIN FETCH u.studyList
                    WHERE t.tokenHash = :tokenHash""";
            return em.createQuery(queryStr, ApiToken.class)
                    .setParameter("tokenHash", tokenHash)
                    .getResultStream()
                    .findFirst();
        });
    }

    public List<ApiToken> findByUser(User user) {
        String queryStr = "SELECT t FROM ApiToken t WHERE t.user = :user";
        return forReadOnly((EntityManager em) ->
                em.createQuery(queryStr, ApiToken.class)
                .setParameter("user", user)
                .getResultList());
    }

}
