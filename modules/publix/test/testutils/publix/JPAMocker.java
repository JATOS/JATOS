package testutils.publix;

import daos.common.AbstractDao;

import jakarta.persistence.EntityManager;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

public class JPAMocker {

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

}