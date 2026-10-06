package testutils.gui;

import daos.common.AbstractDao;
import play.db.jpa.JPAApi;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test utility for stubbing JPA transaction wrappers on mocked {@link JPAApi} and mocked/spied DAOs.
 *
 * In production, {@code JPAApi.withTransaction(...)} opens a transaction and executes the provided callback with an
 * {@link EntityManager}. In unit tests, when {@code JPAApi} or DAO instances are Mockito mocks, those callbacks would
 * not be executed unless explicitly stubbed.
 *
 * This helper configures the transaction methods to immediately invoke the supplied {@code Function} or
 * {@code Consumer} with the provided mocked {@link EntityManager}. It does not create real transactions, commit, roll
 * back, flush, or emulate persistence-context behavior. The afterCommit hook delegates to the real DAO hook using the
 * supplied EntityManager; {@link TransactionCallbacks} can capture and complete it explicitly.
 */
public class JPAMocker {

    /**
     * Captures Hibernate completion callbacks so tests can explicitly commit after checking cleanup timing. Does not
     * emulate database constraints or perform a real database commit.
     */
    public static class TransactionCallbacks {
        public final EntityManager em = mock(EntityManager.class);
        private final List<Synchronization> callbacks = new ArrayList<>();

        public TransactionCallbacks() {
            Session session = mock(Session.class);
            Transaction transaction = mock(Transaction.class);
            when(em.unwrap(Session.class)).thenReturn(session);
            when(session.getTransaction()).thenReturn(transaction);
            when(transaction.isActive()).thenReturn(true);
            doAnswer(call -> {
                callbacks.add(call.getArgument(0));
                return null;
            }).when(transaction).registerSynchronization(any());
        }

        public void commit() {
            List.copyOf(callbacks).forEach(callback -> callback.afterCompletion(Status.STATUS_COMMITTED));
            callbacks.clear();
        }
    }


    @SuppressWarnings("unchecked")
    public static void mockDaoTransactions(EntityManager entityManager, AbstractDao... daos) {
        JPAApi jpa = mock(JPAApi.class);
        when(jpa.em("default")).thenReturn(entityManager);
        AbstractDao completionHooks = new AbstractDao(jpa) {};
        for (AbstractDao dao : daos) {
            doAnswer(invocation -> {
                completionHooks.afterCommit(invocation.getArgument(0), invocation.getArgument(1));
                return null;
            }).when(dao).afterCommit(any(String.class), any(AbstractDao.Cleanup.class));
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