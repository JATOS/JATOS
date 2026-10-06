package daos.common;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Synchronization;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.junit.Test;
import play.db.jpa.JPAApi;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class AbstractDaoTest {

    @Test
    public void missingTransactionDoesNotOpenOne() {
        JPAApi jpa = mock(JPAApi.class);
        AbstractDao dao = new AbstractDao(jpa) {};
        assertThrows(IllegalStateException.class, () -> dao.afterCommit("test", () -> {}));
        verify(jpa).em("default");
        verifyNoMoreInteractions(jpa);
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
        AbstractDao dao = new AbstractDao(jpa) {};
        assertThrows(IllegalStateException.class, () -> dao.afterCommit("test", () -> {}));
        verify(transaction, never()).registerSynchronization(any(Synchronization.class));
        verify(jpa).em("default");
        verifyNoMoreInteractions(jpa);
    }
}
