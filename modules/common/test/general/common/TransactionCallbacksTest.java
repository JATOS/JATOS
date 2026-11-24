package general.common;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import play.db.jpa.JPAApi;

import java.util.function.Function;

import static org.mockito.Mockito.*;

public class TransactionCallbacksTest {

    @Test
    public void onlyKnownOutcomesRunCallbacksAndFailuresDoNotEscape() throws Exception {
        JPAApi delegate = mock(JPAApi.class);
        EntityManager em = mock(EntityManager.class);
        Session session = mock(Session.class);
        Transaction transaction = mock(Transaction.class);
        when(delegate.em("default")).thenReturn(em);
        when(em.unwrap(Session.class)).thenReturn(session);
        when(session.getTransaction()).thenReturn(transaction);
        when(transaction.isActive()).thenReturn(true);
        JpaTransactions api = new JpaTransactions(delegate);
        var committed = mock(JpaTransactions.Cleanup.class);
        var rolledBack = mock(JpaTransactions.Cleanup.class);
        //noinspection unchecked
        when(delegate.withTransaction(any(String.class), anyBoolean(), any(java.util.function.Function.class)))
                .thenAnswer(call -> ((Function<EntityManager, ?>) call.getArgument(2)).apply(em));
        api.withTransaction((java.util.function.Consumer<EntityManager>) ignored -> api.afterCompletion("test", committed, rolledBack));
        var captor = ArgumentCaptor.forClass(Synchronization.class);
        verify(transaction).registerSynchronization(captor.capture());
        captor.getValue().afterCompletion(Status.STATUS_UNKNOWN);
        verifyNoInteractions(committed, rolledBack);
        captor.getValue().afterCompletion(Status.STATUS_ROLLEDBACK);
        verify(rolledBack).run();
        verifyNoInteractions(committed);
        doThrow(new Exception("expected")).when(committed).run();
        captor.getValue().afterCompletion(Status.STATUS_COMMITTED);
        verify(committed).run();
    }
}
