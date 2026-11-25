package testutils.gui;

import general.common.JpaTransactions;

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
 * Stubs service transaction execution; persistence and real completion behavior are tested separately.
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


    /**
     * Execute service transaction blocks against a supplied test EntityManager.
     */
    @SuppressWarnings("unchecked")
    public static void mockTransactions(EntityManager entityManager, JpaTransactions transactions) {
        doAnswer(call -> ((Function<EntityManager, ?>) call.getArgument(0)).apply(entityManager))
                .when(transactions).withTransaction(any(Function.class));
        doAnswer(call -> {
            ((Consumer<EntityManager>) call.getArgument(0)).accept(entityManager);
            return null;
        }).when(transactions).withTransaction(any(Consumer.class));
        doAnswer(call -> ((Function<EntityManager, ?>) call.getArgument(0)).apply(entityManager))
                .when(transactions).forReadOnly(any(Function.class));
        doAnswer(call -> {
            ((Consumer<EntityManager>) call.getArgument(0)).accept(entityManager);
            return null;
        }).when(transactions).forReadOnly(any(Consumer.class));
        doAnswer(call -> ((Function<EntityManager, ?>) call.getArgument(0)).apply(entityManager))
                .when(transactions).withNewTransaction(any(Function.class));
        doAnswer(call -> {
            ((Consumer<EntityManager>) call.getArgument(0)).accept(entityManager);
            return null;
        }).when(transactions).withNewTransaction(any(Consumer.class));
        doAnswer(call -> {
            JpaTransactions.Cleanup callback = call.getArgument(1);
            entityManager.unwrap(Session.class).getTransaction().registerSynchronization(new Synchronization() {
                public void beforeCompletion() {
                }

                public void afterCompletion(int status) {
                    if (status == Status.STATUS_COMMITTED) {
                        try {callback.run();} catch (Exception e) {throw new AssertionError(e);}
                    }
                }
            });
            return null;
        }).when(transactions).afterCommit(any(String.class), any(JpaTransactions.Cleanup.class));
    }
}
