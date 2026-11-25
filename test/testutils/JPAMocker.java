package testutils;

import general.common.JpaTransactions;
import jakarta.persistence.EntityManager;
import org.mockito.ArgumentMatchers;

import java.util.function.Consumer;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Stubs service transaction execution; persistence and real completion behavior are tested separately.
 */
public class JPAMocker {

    public static void mock(JpaTransactions jpa, EntityManager entityManager) {
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
