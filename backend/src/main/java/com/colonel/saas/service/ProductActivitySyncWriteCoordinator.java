package com.colonel.saas.service;

import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 * 活动商品同步的本地写入协调器。
 *
 * <p>远程分页必须在调用方完成；本类只负责把已经取得的商品事实按稳定
 * {@code productId} 顺序拆成独立子事务写入，避免两个活动以不同顺序竞争相同商品行。</p>
 */
@Slf4j
@Service
public class ProductActivitySyncWriteCoordinator {

    private static final String POSTGRES_DEADLOCK_SQL_STATE = "40P01";
    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final int DEFAULT_RETRY_MAX = 3;

    private final TransactionTemplate transactionTemplate;
    private final int batchSize;
    private final int retryMax;
    private final BackoffSleeper backoffSleeper;

    @Autowired
    public ProductActivitySyncWriteCoordinator(
            PlatformTransactionManager transactionManager,
            @Value("${product.sync.activityProduct.writeBatchSize:100}") int batchSize,
            @Value("${product.sync.activityProduct.deadlockRetryMax:3}") int retryMax) {
        this(transactionManager, batchSize, retryMax, ProductActivitySyncWriteCoordinator::sleep);
    }

    ProductActivitySyncWriteCoordinator(
            PlatformTransactionManager transactionManager,
            int batchSize,
            int retryMax,
            BackoffSleeper backoffSleeper) {
        this.transactionTemplate = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager"));
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setName("product-activity-sync-batch");
        this.batchSize = Math.max(1, batchSize > 0 ? batchSize : DEFAULT_BATCH_SIZE);
        this.retryMax = Math.max(0, retryMax >= 0 ? retryMax : DEFAULT_RETRY_MAX);
        this.backoffSleeper = Objects.requireNonNull(backoffSleeper, "backoffSleeper");
    }

    /**
     * 按商品 ID 排序后分批执行本地写入。每个批次都是独立的 REQUIRES_NEW 事务。
     * writer 不得执行远程请求，只允许完成本地事实层及其受控派生写入。
     */
    public <T> List<T> executeInBatches(
            String activityId,
            List<DouyinProductGateway.ActivityProductItem> items,
            Function<List<DouyinProductGateway.ActivityProductItem>, T> writer) {
        Objects.requireNonNull(activityId, "activityId");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(writer, "writer");
        if (items.isEmpty()) {
            return List.of();
        }

        List<DouyinProductGateway.ActivityProductItem> sortedItems = new ArrayList<>(items);
        sortedItems.sort(Comparator.comparingLong(DouyinProductGateway.ActivityProductItem::productId));
        List<T> results = new ArrayList<>((sortedItems.size() + batchSize - 1) / batchSize);
        int completedBatchCount = 0;

        for (int from = 0; from < sortedItems.size(); from += batchSize) {
            int to = Math.min(sortedItems.size(), from + batchSize);
            List<DouyinProductGateway.ActivityProductItem> batch =
                    List.copyOf(sortedItems.subList(from, to));
            int retryCount = 0;
            int maxAttempts = retryMax + 1;

            while (true) {
                try {
                    T result = transactionTemplate.execute(status -> writer.apply(batch));
                    results.add(result);
                    completedBatchCount++;
                    break;
                } catch (RuntimeException ex) {
                    if (!isPostgresDeadlock(ex)) {
                        throw ex;
                    }
                    if (retryCount >= retryMax) {
                        throw new DeadlockRetryExhaustedException(
                                activityId, retryCount, completedBatchCount, maxAttempts, ex);
                    }
                    retryCount++;
                    long delayMs = backoffDelayMillis(retryCount);
                    log.warn(
                            "product activity sync batch deadlock loser, activityId={}, batchSize={}, "
                                    + "retry={}/{}, sqlState={}",
                            activityId, batch.size(), retryCount, retryMax, POSTGRES_DEADLOCK_SQL_STATE);
                    try {
                        backoffSleeper.sleep(delayMs);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new DeadlockRetryInterruptedException(activityId, completedBatchCount, interrupted);
                    }
                }
            }
        }
        return results;
    }

    private long backoffDelayMillis(int retryNumber) {
        long base = 200L * (1L << Math.min(retryNumber - 1, 3));
        return base + ThreadLocalRandom.current().nextLong(0, 100L);
    }

    private boolean isPostgresDeadlock(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof DeadlockLoserDataAccessException) {
                return true;
            }
            if (current instanceof SQLException sqlException
                    && POSTGRES_DEADLOCK_SQL_STATE.equals(sqlException.getSQLState())) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toUpperCase().contains(POSTGRES_DEADLOCK_SQL_STATE)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void sleep(long delayMs) throws InterruptedException {
        Thread.sleep(delayMs);
    }

    @FunctionalInterface
    interface BackoffSleeper {
        void sleep(long delayMs) throws InterruptedException;
    }

    public static final class DeadlockRetryExhaustedException extends RuntimeException {
        private final String activityId;
        private final int retryCount;
        private final int completedBatchCount;
        private final int maxAttempts;

        private DeadlockRetryExhaustedException(
                String activityId,
                int retryCount,
                int completedBatchCount,
                int maxAttempts,
                Throwable cause) {
            super("活动商品同步批次死锁重试耗尽", cause);
            this.activityId = activityId;
            this.retryCount = retryCount;
            this.completedBatchCount = completedBatchCount;
            this.maxAttempts = maxAttempts;
        }

        public String activityId() {
            return activityId;
        }

        public int retryCount() {
            return retryCount;
        }

        public int completedBatchCount() {
            return completedBatchCount;
        }

        public int maxAttempts() {
            return maxAttempts;
        }
    }

    private static final class DeadlockRetryInterruptedException extends RuntimeException {
        private DeadlockRetryInterruptedException(String activityId, int completedBatchCount, Throwable cause) {
            super("活动商品同步批次重试被中断", cause);
        }
    }
}
