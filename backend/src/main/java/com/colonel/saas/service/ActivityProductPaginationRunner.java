package com.colonel.saas.service;

import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.IntConsumer;

/**
 * 活动商品游标分页执行器。
 *
 * <p>只负责分页边界、stopReason 和汇总统计；是否写库由调用方通过 {@link PageHandler} 决定。</p>
 */
public final class ActivityProductPaginationRunner {

    private ActivityProductPaginationRunner() {
    }

    public enum StopReason {
        DONE_NO_MORE,
        MAX_PAGES_REACHED,
        MAX_ROWS_REACHED,
        REPEATED_CURSOR,
        EMPTY_CURSOR_WITH_HAS_NEXT,
        EMPTY_PAGE_WITH_HAS_NEXT,
        API_ERROR,
        INVALID_RESPONSE,
        MANUAL_LIMIT,
        UNKNOWN
    }

    public record Options(int pageSize, int maxPages, int maxRows, boolean stopOnRepeatedCursor) {
        Options normalized() {
            int normalizedPageSize = Math.min(Math.max(pageSize <= 0 ? 20 : pageSize, 1), 20);
            int normalizedMaxPages = Math.max(maxPages <= 0 ? 1000 : maxPages, 1);
            int normalizedMaxRows = Math.max(maxRows <= 0 ? 50_000 : maxRows, 1);
            return new Options(normalizedPageSize, normalizedMaxPages, normalizedMaxRows, stopOnRepeatedCursor);
        }
    }

    public record PageSummary(
            int pageNo,
            String requestCursor,
            String nextCursor,
            int returned,
            boolean hasNext,
            String firstProductId,
            String lastProductId,
            int duplicateInRun,
            long elapsedMs) {
    }

    public record PageWriteStats(int createdCount, int updatedCount, int skippedCount, int libraryEntryCount) {
        public static final PageWriteStats ZERO = new PageWriteStats(0, 0, 0, 0);
    }

    public record PageContext(
            int pageNo,
            DouyinProductGateway.ActivityProductQueryRequest request,
            DouyinProductGateway.ActivityProductListResult response,
            List<DouyinProductGateway.ActivityProductItem> items,
            PageSummary summary) {
    }

    @FunctionalInterface
    public interface PageHandler {
        PageWriteStats handle(PageContext page);
    }

    public record Result(
            int pagesFetched,
            int fetchedRows,
            int distinctProductIds,
            int duplicateProductIds,
            int createdCount,
            int updatedCount,
            int skippedCount,
            int libraryEntryCount,
            StopReason stopReason,
            boolean stillHasNextWhenStopped,
            boolean complete,
            String lastCursor,
            List<PageSummary> pageSamples,
            List<String> warnings,
            Set<String> productIds) {
    }

    public static Result run(
            DouyinProductGateway.ActivityProductQueryRequest baseRequest,
            Options options,
            Function<DouyinProductGateway.ActivityProductQueryRequest, DouyinProductGateway.ActivityProductListResult> fetcher,
            PageHandler pageHandler,
            IntConsumer beforeNextPage) {
        if (baseRequest == null || !StringUtils.hasText(baseRequest.activityId())) {
            return empty(StopReason.INVALID_RESPONSE, "activityId is required");
        }
        Options normalized = options == null ? new Options(20, 1000, 50_000, true).normalized() : options.normalized();
        PageHandler handler = pageHandler == null ? page -> PageWriteStats.ZERO : pageHandler;
        IntConsumer continueHook = beforeNextPage == null ? page -> { } : beforeNextPage;

        String cursor = normalizeCursor(baseRequest.cursor());
        String lastCursor = cursor;
        Set<String> seenCursors = new LinkedHashSet<>();
        Set<String> productIds = new LinkedHashSet<>();
        List<PageSummary> pageSamples = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int pagesFetched = 0;
        int fetchedRows = 0;
        int duplicateProductIds = 0;
        int createdCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;
        int libraryEntryCount = 0;
        if (StringUtils.hasText(cursor)) {
            seenCursors.add(cursor);
        }

        while (pagesFetched < normalized.maxPages()) {
            DouyinProductGateway.ActivityProductQueryRequest pageRequest = pageRequest(baseRequest, normalized.pageSize(), cursor);
            long startedAt = System.nanoTime();
            DouyinProductGateway.ActivityProductListResult page;
            try {
                page = fetcher.apply(pageRequest);
            } catch (RuntimeException ex) {
                warnings.add("api error at page " + (pagesFetched + 1) + ": " + ex.getMessage());
                return result(pagesFetched, fetchedRows, productIds, duplicateProductIds, createdCount, updatedCount,
                        skippedCount, libraryEntryCount, StopReason.API_ERROR, true, false, lastCursor, pageSamples, warnings);
            }
            pagesFetched++;
            if (page == null) {
                warnings.add("null response at page " + pagesFetched);
                return result(pagesFetched, fetchedRows, productIds, duplicateProductIds, createdCount, updatedCount,
                        skippedCount, libraryEntryCount, StopReason.INVALID_RESPONSE, true, false, lastCursor, pageSamples, warnings);
            }
            List<DouyinProductGateway.ActivityProductItem> rawItems = page.items() == null ? List.of() : page.items();
            int remainingRows = normalized.maxRows() - fetchedRows;
            boolean maxRowsReached = rawItems.size() > remainingRows;
            List<DouyinProductGateway.ActivityProductItem> items = maxRowsReached
                    ? rawItems.subList(0, Math.max(0, remainingRows))
                    : rawItems;
            int duplicateInPage = 0;
            for (DouyinProductGateway.ActivityProductItem item : items) {
                String productId = productId(item);
                if (!StringUtils.hasText(productId)) {
                    continue;
                }
                if (!productIds.add(productId)) {
                    duplicateProductIds++;
                    duplicateInPage++;
                }
            }
            fetchedRows += items.size();
            String nextCursor = normalizeCursor(page.nextCursor());
            boolean hasNext = isTraversableCursor(nextCursor);
            PageSummary summary = new PageSummary(
                    pagesFetched,
                    cursor,
                    nextCursor,
                    rawItems.size(),
                    hasNext,
                    rawItems.isEmpty() ? null : productId(rawItems.get(0)),
                    rawItems.isEmpty() ? null : productId(rawItems.get(rawItems.size() - 1)),
                    duplicateInPage,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
            pageSamples.add(summary);
            PageWriteStats stats = handler.handle(new PageContext(pagesFetched, pageRequest, page, items, summary));
            if (stats != null) {
                createdCount += stats.createdCount();
                updatedCount += stats.updatedCount();
                skippedCount += stats.skippedCount();
                libraryEntryCount += stats.libraryEntryCount();
            }
            lastCursor = nextCursor;

            if (maxRowsReached) {
                return result(pagesFetched, fetchedRows, productIds, duplicateProductIds, createdCount, updatedCount,
                        skippedCount, libraryEntryCount, StopReason.MAX_ROWS_REACHED, hasNext, false, lastCursor, pageSamples, warnings);
            }
            if (rawItems.isEmpty()) {
                StopReason reason = hasNext ? StopReason.EMPTY_PAGE_WITH_HAS_NEXT : StopReason.DONE_NO_MORE;
                return result(pagesFetched, fetchedRows, productIds, duplicateProductIds, createdCount, updatedCount,
                        skippedCount, libraryEntryCount, reason, hasNext, reason == StopReason.DONE_NO_MORE, lastCursor, pageSamples, warnings);
            }
            if (!hasNext) {
                boolean totalIndicatesMore = page.total() != null && page.total() > fetchedRows;
                StopReason reason = totalIndicatesMore ? StopReason.EMPTY_CURSOR_WITH_HAS_NEXT : StopReason.DONE_NO_MORE;
                return result(pagesFetched, fetchedRows, productIds, duplicateProductIds, createdCount, updatedCount,
                        skippedCount, libraryEntryCount, reason, totalIndicatesMore, reason == StopReason.DONE_NO_MORE, lastCursor, pageSamples, warnings);
            }
            if (fetchedRows >= normalized.maxRows()) {
                return result(pagesFetched, fetchedRows, productIds, duplicateProductIds, createdCount, updatedCount,
                        skippedCount, libraryEntryCount, StopReason.MAX_ROWS_REACHED, true, false, lastCursor, pageSamples, warnings);
            }
            if (pagesFetched >= normalized.maxPages()) {
                return result(pagesFetched, fetchedRows, productIds, duplicateProductIds, createdCount, updatedCount,
                        skippedCount, libraryEntryCount, StopReason.MAX_PAGES_REACHED, true, false, lastCursor, pageSamples, warnings);
            }
            if (normalized.stopOnRepeatedCursor()
                    && (nextCursor.equals(cursor) || seenCursors.contains(nextCursor))) {
                warnings.add("repeated cursor detected: " + nextCursor);
                return result(pagesFetched, fetchedRows, productIds, duplicateProductIds, createdCount, updatedCount,
                        skippedCount, libraryEntryCount, StopReason.REPEATED_CURSOR, true, false, lastCursor, pageSamples, warnings);
            }
            seenCursors.add(nextCursor);
            cursor = nextCursor;
            continueHook.accept(pagesFetched);
        }

        return result(pagesFetched, fetchedRows, productIds, duplicateProductIds, createdCount, updatedCount,
                skippedCount, libraryEntryCount, StopReason.MAX_PAGES_REACHED, true, false, lastCursor, pageSamples, warnings);
    }

    private static Result empty(StopReason stopReason, String warning) {
        return new Result(0, 0, 0, 0, 0, 0, 0, 0,
                stopReason, false, false, null, List.of(), List.of(warning), Set.of());
    }

    private static Result result(
            int pagesFetched,
            int fetchedRows,
            Set<String> productIds,
            int duplicateProductIds,
            int createdCount,
            int updatedCount,
            int skippedCount,
            int libraryEntryCount,
            StopReason stopReason,
            boolean stillHasNextWhenStopped,
            boolean complete,
            String lastCursor,
            List<PageSummary> pageSamples,
            List<String> warnings) {
        return new Result(
                pagesFetched,
                fetchedRows,
                productIds.size(),
                duplicateProductIds,
                createdCount,
                updatedCount,
                skippedCount,
                libraryEntryCount,
                stopReason,
                stillHasNextWhenStopped,
                complete,
                lastCursor,
                List.copyOf(pageSamples),
                List.copyOf(warnings),
                Set.copyOf(productIds));
    }

    private static DouyinProductGateway.ActivityProductQueryRequest pageRequest(
            DouyinProductGateway.ActivityProductQueryRequest baseRequest,
            int pageSize,
            String cursor) {
        return new DouyinProductGateway.ActivityProductQueryRequest(
                baseRequest.appId(),
                baseRequest.activityId(),
                baseRequest.searchType(),
                baseRequest.sortType(),
                pageSize,
                baseRequest.cooperationInfo(),
                baseRequest.cooperationType(),
                baseRequest.productInfo(),
                baseRequest.status(),
                1L,
                cursor,
                null);
    }

    private static String productId(DouyinProductGateway.ActivityProductItem item) {
        return item == null ? null : String.valueOf(item.productId());
    }

    private static boolean isTraversableCursor(String cursor) {
        return StringUtils.hasText(cursor) && !"0".equals(cursor);
    }

    private static String normalizeCursor(String cursor) {
        return StringUtils.hasText(cursor) ? cursor.trim() : "";
    }
}
