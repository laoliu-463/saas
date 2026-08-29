package com.colonel.saas.domain.performance.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.colonel.saas.entity.PerformanceCalculationExecution;
import com.colonel.saas.mapper.PerformanceCalculationExecutionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 业绩计算执行幂等和失败重试状态管理。 */
@Service
public class PerformanceCalculationExecutionService {

    public static final String RUNNING = "RUNNING";
    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String FAILED = "FAILED";

    private final PerformanceCalculationExecutionMapper mapper;

    public PerformanceCalculationExecutionService(PerformanceCalculationExecutionMapper mapper) {
        this.mapper = mapper;
    }

    /** 占用事件执行权；成功事件不重复执行，失败事件可由新投递或重试任务恢复。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean start(String eventKey, String eventType, String orderId, int orderVersion) {
        return start(eventKey, eventType, orderId, orderVersion, null);
    }

    /**
     * 占用事件执行权，并固化能让失败任务完整恢复的最小事件输入快照。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean start(
            String eventKey,
            String eventType,
            String orderId,
            int orderVersion,
            Map<String, Object> eventPayload) {
        PerformanceCalculationExecution existing = mapper.selectOne(new LambdaQueryWrapper<PerformanceCalculationExecution>()
                .eq(PerformanceCalculationExecution::getEventKey, eventKey)
                .last("LIMIT 1"));
        if (existing != null && SUCCEEDED.equals(existing.getStatus())) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            PerformanceCalculationExecution execution = new PerformanceCalculationExecution();
            execution.setId(UUID.randomUUID());
            execution.setEventKey(eventKey);
            execution.setEventType(eventType);
            execution.setOrderId(orderId);
            execution.setOrderVersion(orderVersion);
            execution.setEventPayload(eventPayload == null ? Map.of() : new LinkedHashMap<>(eventPayload));
            execution.setStatus(RUNNING);
            execution.setRetryCount(0);
            execution.setStartedAt(now);
            execution.setCreatedAt(now);
            execution.setUpdatedAt(now);
            mapper.insert(execution);
        } else {
            LambdaUpdateWrapper<PerformanceCalculationExecution> update = new LambdaUpdateWrapper<PerformanceCalculationExecution>()
                    .eq(PerformanceCalculationExecution::getId, existing.getId())
                    .set(PerformanceCalculationExecution::getStatus, RUNNING)
                    .set(PerformanceCalculationExecution::getLastError, null)
                    .set(PerformanceCalculationExecution::getNextRetryAt, null)
                    .set(PerformanceCalculationExecution::getStartedAt, now)
                    .set(PerformanceCalculationExecution::getUpdatedAt, now);
            if (eventPayload != null) {
                update.set(PerformanceCalculationExecution::getEventPayload, new LinkedHashMap<>(eventPayload));
            }
            mapper.update(null, update);
        }
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSucceeded(String eventKey) {
        LocalDateTime now = LocalDateTime.now();
        mapper.update(null, new LambdaUpdateWrapper<PerformanceCalculationExecution>()
                .eq(PerformanceCalculationExecution::getEventKey, eventKey)
                .set(PerformanceCalculationExecution::getStatus, SUCCEEDED)
                .set(PerformanceCalculationExecution::getCompletedAt, now)
                .set(PerformanceCalculationExecution::getNextRetryAt, null)
                .set(PerformanceCalculationExecution::getUpdatedAt, now));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String eventKey, Throwable error) {
        PerformanceCalculationExecution existing = mapper.selectOne(new LambdaQueryWrapper<PerformanceCalculationExecution>()
                .eq(PerformanceCalculationExecution::getEventKey, eventKey)
                .last("LIMIT 1"));
        if (existing == null) {
            return;
        }
        int retryCount = (existing.getRetryCount() == null ? 0 : existing.getRetryCount()) + 1;
        LocalDateTime now = LocalDateTime.now();
        mapper.update(null, new LambdaUpdateWrapper<PerformanceCalculationExecution>()
                .eq(PerformanceCalculationExecution::getId, existing.getId())
                .set(PerformanceCalculationExecution::getStatus, FAILED)
                .set(PerformanceCalculationExecution::getRetryCount, retryCount)
                .set(PerformanceCalculationExecution::getLastError, compactError(error))
                .set(PerformanceCalculationExecution::getNextRetryAt, now.plusMinutes(Math.min(5L * retryCount, 60L)))
                .set(PerformanceCalculationExecution::getUpdatedAt, now));
    }

    public List<PerformanceCalculationExecution> findRetryDue(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return mapper.selectList(new LambdaQueryWrapper<PerformanceCalculationExecution>()
                .eq(PerformanceCalculationExecution::getStatus, FAILED)
                .le(PerformanceCalculationExecution::getNextRetryAt, LocalDateTime.now())
                .orderByAsc(PerformanceCalculationExecution::getNextRetryAt)
                .last("LIMIT " + safeLimit));
    }

    private static String compactError(Throwable error) {
        String message = error == null ? "unknown calculation failure" : error.toString();
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
