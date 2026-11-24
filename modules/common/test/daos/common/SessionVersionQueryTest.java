package daos.common;

import general.common.JpaTransactions;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.Test;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class SessionVersionQueryTest {

    @Test public void batchVersionQueryDoesNotLoadEntity() {
        verifyQuery("SELECT s.batchSessionVersion FROM Batch s WHERE s.id = :id",
                api -> new BatchDao(api).findSessionVersion(1L));
    }

    @Test public void groupVersionQueryDoesNotLoadEntity() {
        verifyQuery("SELECT s.groupSessionVersion FROM GroupResult s WHERE s.id = :id",
                api -> new GroupResultDao(api).findSessionVersion(1L));
    }

    @SuppressWarnings("unchecked")
    private void verifyQuery(String jpql, Function<JpaTransactions, Optional<Long>> find) {
        JpaTransactions api = mock(JpaTransactions.class);
        EntityManager em = mock(EntityManager.class);
        TypedQuery<Long> query = mock(TypedQuery.class);
        when(api.withReadOnlyTransaction(any(Function.class)))
                .thenAnswer(call -> ((Function<EntityManager, ?>) call.getArgument(0)).apply(em));
        when(em.createQuery(jpql, Long.class)).thenReturn(query);
        when(query.setParameter("id", 1L)).thenReturn(query);
        when(query.getResultStream()).thenReturn(Stream.of(7L));
        assertEquals(Optional.of(7L), find.apply(api));
        verify(em).createQuery(jpql, Long.class);
        verifyNoMoreInteractions(em);
    }
}
