package com.colonel.saas.service;

import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductActivitySyncWriteCoordinatorTest {

    @Test
    void concurrentActivitiesMustUseTheSameProductLockOrder() throws Exception {
        ProductActivitySyncWriteCoordinator coordinator = coordinator(100, 3, ignored -> {
        });
        CountDownLatch writersStarted = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<List<Long>> first = executor.submit(() -> coordinator.executeInBatches(
                    "3223881",
                    items(3, 1, 2),
                    batch -> {
                        writersStarted.countDown();
                        awaitBothWriters(writersStarted);
                        return batch.stream().map(DouyinProductGateway.ActivityProductItem::productId).toList();
                    }).get(0));
            Future<List<Long>> second = executor.submit(() -> coordinator.executeInBatches(
                    "3916506",
                    items(2, 3, 1),
                    batch -> {
                        writersStarted.countDown();
                        awaitBothWriters(writersStarted);
                        return batch.stream().map(DouyinProductGateway.ActivityProductItem::productId).toList();
                    }).get(0));

            assertThat(first.get(3, TimeUnit.SECONDS)).containsExactly(1L, 2L, 3L);
            assertThat(second.get(3, TimeUnit.SECONDS)).containsExactly(1L, 2L, 3L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void writesSortedBatchesInIndependentTransactions() {
        PlatformTransactionManager transactionManager = transactionManager();
        ProductActivitySyncWriteCoordinator coordinator = coordinator(transactionManager, 2, 3, ignored -> {
        });
        List<List<Long>> batches = coordinator.executeInBatches(
                "3223881",
                items(5, 1, 4, 2, 3),
                batch -> batch.stream().map(DouyinProductGateway.ActivityProductItem::productId).toList());

        assertThat(batches).containsExactly(
                List.of(1L, 2L),
                List.of(3L, 4L),
                List.of(5L));
        verify(transactionManager, times(3)).getTransaction(any());
        verify(transactionManager, times(3)).commit(any());
    }

    @Test
    void retriesOnlyPostgresDeadlockLoserWithoutRefetchingItems() {
        AtomicInteger attempts = new AtomicInteger();
        List<Long> sleeps = new ArrayList<>();
        ProductActivitySyncWriteCoordinator coordinator = coordinator(100, 3, sleeps::add);

        List<List<Long>> batches = coordinator.executeInBatches(
                "3223881",
                items(2, 1),
                batch -> {
                    if (attempts.getAndIncrement() == 0) {
                        throw new DeadlockLoserDataAccessException("deadlock detected 40P01", null);
                    }
                    return batch.stream().map(DouyinProductGateway.ActivityProductItem::productId).toList();
                });

        assertThat(batches).containsExactly(List.of(1L, 2L));
        assertThat(attempts).hasValue(2);
        assertThat(sleeps).hasSize(1);
        assertThat(sleeps.get(0)).isGreaterThanOrEqualTo(200L).isLessThan(300L);
    }

    @Test
    void reportsRetryExhaustionAndCompletedBatchCount() {
        List<Long> sleeps = new ArrayList<>();
        ProductActivitySyncWriteCoordinator coordinator = coordinator(2, 3, sleeps::add);

        assertThatThrownBy(() -> coordinator.executeInBatches(
                "3916506",
                items(4, 3, 2, 1),
                batch -> {
                    if (batch.get(0).productId() != 3L) {
                        return List.of(1L);
                    }
                    throw new DeadlockLoserDataAccessException("deadlock detected 40P01", null);
                }))
                .isInstanceOf(ProductActivitySyncWriteCoordinator.DeadlockRetryExhaustedException.class)
                .satisfies(error -> {
                    ProductActivitySyncWriteCoordinator.DeadlockRetryExhaustedException exhausted =
                            (ProductActivitySyncWriteCoordinator.DeadlockRetryExhaustedException) error;
                    assertThat(exhausted.retryCount()).isEqualTo(3);
                    assertThat(exhausted.completedBatchCount()).isEqualTo(1);
                });
        assertThat(sleeps).hasSize(3);
    }

    private ProductActivitySyncWriteCoordinator coordinator(int batchSize, int retryMax,
                                                             ProductActivitySyncWriteCoordinator.BackoffSleeper sleeper) {
        return coordinator(transactionManager(), batchSize, retryMax, sleeper);
    }

    private ProductActivitySyncWriteCoordinator coordinator(PlatformTransactionManager transactionManager,
                                                             int batchSize,
                                                             int retryMax,
                                                             ProductActivitySyncWriteCoordinator.BackoffSleeper sleeper) {
        return new ProductActivitySyncWriteCoordinator(transactionManager, batchSize, retryMax, sleeper);
    }

    private PlatformTransactionManager transactionManager() {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        doNothing().when(transactionManager).commit(any());
        doNothing().when(transactionManager).rollback(any());
        return transactionManager;
    }

    private void awaitBothWriters(CountDownLatch writersStarted) {
        try {
            assertThat(writersStarted.await(2, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("concurrent writer test interrupted", interrupted);
        }
    }

    private List<DouyinProductGateway.ActivityProductItem> items(long... productIds) {
        List<DouyinProductGateway.ActivityProductItem> items = new ArrayList<>();
        for (long productId : productIds) {
            items.add(new DouyinProductGateway.ActivityProductItem(
                    productId,
                    "product-" + productId,
                    null,
                    0L,
                    null,
                    0L,
                    0L,
                    0L,
                    null,
                    0,
                    null,
                    null,
                    null,
                    false,
                    false,
                    0L,
                    0L,
                    null,
                    null,
                    1,
                    "推广中",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    Map.of()));
        }
        return items;
    }
}
