package testutils;

import daos.common.AbstractDao;
import org.mockito.ArgumentMatchers;
import play.db.jpa.JPAApi;

import jakarta.persistence.EntityManager;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Test utility for stubbing JPA transaction wrappers on mocked {@link JPAApi} and mocked/spied DAOs.
 *
 * In production, {@code JPAApi.withTransaction(...)} opens a transaction and executes the provided callback with an
 * {@link EntityManager}. In unit tests, when {@code JPAApi} or DAO instances are Mockito mocks, those callbacks would
 * not be executed unless explicitly stubbed.
 *
 * This helper configures the transaction methods to immediately invoke the supplied {@code Function} or
 * {@code Consumer} with the provided mocked {@link EntityManager}. It does not create real transactions, commit, roll
 * back, flush, or emulate persistence-context behavior.
 */
public class JPAMocker {

    public static void mock(JPAApi jpa, EntityManager entityManager) {
        doAnswer(invocation -> {
            Function<EntityManager, ?> block = invocation.getArgument(0);
            return block.apply(entityManager);
        }).when(jpa).withTransaction(ArgumentMatchers.<Function<EntityManager, ?>>any());

        doAnswer(invocation -> {
            Consumer<EntityManager> block = invocation.getArgument(0);
            block.accept(entityManager);
            return null;
        }).when(jpa).withTransaction(ArgumentMatchers.<Consumer<EntityManager>>any());

        doAnswer(invocation -> {
            Function<EntityManager, ?> block = invocation.getArgument(2);
            return block.apply(entityManager);
        }).when(jpa).withTransaction(
                any(String.class),
                any(Boolean.class),
                ArgumentMatchers.<Function<EntityManager, ?>>any());

        doAnswer(invocation -> {
            Consumer<EntityManager> block = invocation.getArgument(2);
            block.accept(entityManager);
            return null;
        }).when(jpa).withTransaction(
                any(String.class),
                any(Boolean.class),
                ArgumentMatchers.<Consumer<EntityManager>>any());
    }

    @SuppressWarnings("unchecked")
    public static void mockDaoTransactions(EntityManager entityManager, AbstractDao... daos) {
        for (AbstractDao dao : daos) {
            doAnswer(invocation -> {
                Function<EntityManager, Object> block = invocation.getArgument(0);
                return block.apply(entityManager);
            }).when(dao).withReadOnlyTransaction(any(Function.class));

            doAnswer(invocation -> {
                Consumer<EntityManager> block = invocation.getArgument(0);
                block.accept(entityManager);
                return null;
            }).when(dao).withReadOnlyTransaction(any(Consumer.class));

            doAnswer(invocation -> {
                Function<EntityManager, Object> block = invocation.getArgument(0);
                return block.apply(entityManager);
            }).when(dao).withTransaction(any(Function.class));

            doAnswer(invocation -> {
                Consumer<EntityManager> block = invocation.getArgument(0);
                block.accept(entityManager);
                return null;
            }).when(dao).withTransaction(any(Consumer.class));
        }
    }

    public static void mockDaoTransactions(AbstractDao... daos) {
        mockDaoTransactions(null, daos);
    }
}