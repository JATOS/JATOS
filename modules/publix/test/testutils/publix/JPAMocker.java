package testutils.publix;

import general.common.JpaTransactions;

import jakarta.persistence.EntityManager;

import java.util.function.Consumer;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

public class JPAMocker {

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
    }
}
