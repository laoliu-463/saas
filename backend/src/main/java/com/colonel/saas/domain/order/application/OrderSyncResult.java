package com.colonel.saas.domain.order.application;

import com.colonel.saas.domain.order.application.OrderSyncService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 订单同步应用层结果（DDD-ORDER-001）。
 */
public class OrderSyncResult {

    private long startTime;
    private long endTime;
    private int fetched;
    private int inserted;
    private int updated;
    private int skipped;
    private int failed;
    private long checkpointBefore;
    private long checkpointAfter;
    private List<String> errors = new ArrayList<>();
    private long durationMs;
    private boolean locked;
    private int pages;
    private int attributed;
    private String stopReason;

    public static OrderSyncResult fromLegacy(
            OrderSyncService.SyncResult legacy,
            long checkpointBefore,
            long checkpointAfter,
            long durationMs) {
        OrderSyncResult result = new OrderSyncResult();
        result.startTime = legacy.startTime();
        result.endTime = legacy.endTime();
        result.pages = legacy.pages();
        result.fetched = legacy.totalFetched();
        result.inserted = legacy.created();
        result.updated = legacy.updated();
        result.skipped = legacy.unattributed();
        result.failed = legacy.failed();
        result.attributed = legacy.attributed();
        result.locked = legacy.locked();
        result.stopReason = legacy.stopReason();
        result.checkpointBefore = checkpointBefore;
        result.checkpointAfter = checkpointAfter;
        result.durationMs = durationMs;
        return result;
    }

    public static OrderSyncResult dryRunSkipped(long checkpoint, long startedAtMs) {
        OrderSyncResult result = new OrderSyncResult();
        result.checkpointBefore = checkpoint;
        result.checkpointAfter = checkpoint;
        result.durationMs = System.currentTimeMillis() - startedAtMs;
        result.errors = List.of("DRY_RUN: order sync skipped; legacy OrderSyncService has no dry-run path");
        return result;
    }

    public OrderSyncService.SyncResult toLegacySyncResult() {
        return new OrderSyncService.SyncResult(
                startTime,
                endTime,
                pages,
                fetched,
                inserted,
                updated,
                attributed,
                skipped,
                failed,
                locked,
                fetched,
                stopReason);
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public int getFetched() {
        return fetched;
    }

    public int getInserted() {
        return inserted;
    }

    public int getUpdated() {
        return updated;
    }

    public int getSkipped() {
        return skipped;
    }

    public int getFailed() {
        return failed;
    }

    public long getCheckpointBefore() {
        return checkpointBefore;
    }

    public long getCheckpointAfter() {
        return checkpointAfter;
    }

    public List<String> getErrors() {
        return errors == null ? Collections.emptyList() : errors;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public boolean isLocked() {
        return locked;
    }

    public int getPages() {
        return pages;
    }

    public int getAttributed() {
        return attributed;
    }

    public String getStopReason() {
        return stopReason;
    }
}
