package general.common;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Synchronization;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.junit.Test;
import play.db.jpa.JPAApi;

import java.util.function.Function;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class TransactionRegistrationTest {

    @Test
    public void missingTransactionDoesNotOpenOne() {
        JPAApi jpa = mock(JPAApi.class);
        JpaTransactions transactions = new JpaTransactions(jpa);
        assertThrows(IllegalStateException.class, () -> transactions.afterCommit("test", () -> {}));
        verifyNoInteractions(jpa);
    }

    @Test
    public void inactiveTransactionRejectsRegistration() {
        JPAApi jpa = mock(JPAApi.class);
        EntityManager em = mock(EntityManager.class);
        Session session = mock(Session.class);
        Transaction transaction = mock(Transaction.class);
        when(jpa.em("default")).thenReturn(em);
        when(em.unwrap(Session.class)).thenReturn(session);
        when(session.getTransaction()).thenReturn(transaction);
        JpaTransactions transactions = new JpaTransactions(jpa);
        //noinspection unchecked
        when(jpa.withTransaction(any(String.class), anyBoolean(), any(java.util.function.Function.class)))
                .thenAnswer(call -> ((Function<EntityManager, ?>) call.getArgument(2)).apply(em));
        assertThrows(IllegalStateException.class, () -> transactions.withTransaction((java.util.function.Consumer<EntityManager>) ignored -> transactions.afterCommit("test", () -> {})));
        verify(transaction, never()).registerSynchronization(any(Synchronization.class));

    }
}
