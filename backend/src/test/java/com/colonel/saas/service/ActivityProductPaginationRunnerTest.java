package com.colonel.saas.service;

import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityProductPaginationRunnerTest {

    @Test
    void run_shouldStopAtMaxPagesWhenPage101StillHasCursor() {
        List<DouyinProductGateway.ActivityProductQueryRequest> requests = new ArrayList<>();

        ActivityProductPaginationRunner.Result result = ActivityProductPaginationRunner.run(
                request("3859423", 20, null),
                new ActivityProductPaginationRunner.Options(20, 100, 20_000, true),
                pageRequest -> {
                    requests.add(pageRequest);
                    int page = requests.size();
                    return page(page, true);
                },
                page -> ActivityProductPaginationRunner.PageWriteStats.ZERO,
                page -> {
                });

        assertThat(result.pagesFetched()).isEqualTo(100);
        assertThat(result.fetchedRows()).isEqualTo(2_000);
        assertThat(result.stopReason()).isEqualTo(ActivityProductPaginationRunner.StopReason.MAX_PAGES_REACHED);
        assertThat(result.stillHasNextWhenStopped()).isTrue();
        assertThat(result.complete()).isFalse();
        assertThat(requests.get(99).cursor()).isEqualTo("1980");
    }

    @Test
    void run_shouldFetchAll105PagesWhenMaxPagesAllowsIt() {
        ActivityProductPaginationRunner.Result result = ActivityProductPaginationRunner.run(
                request("3859423", 20, null),
                new ActivityProductPaginationRunner.Options(20, 300, 20_000, true),
                pageRequest -> {
                    int cursor = pageRequest.cursor() == null || pageRequest.cursor().isBlank()
                            ? 0
                            : Integer.parseInt(pageRequest.cursor());
                    int page = (cursor / 20) + 1;
                    return page(page, page < 105);
                },
                page -> ActivityProductPaginationRunner.PageWriteStats.ZERO,
                page -> {
                });

        assertThat(result.pagesFetched()).isEqualTo(105);
        assertThat(result.fetchedRows()).isEqualTo(2_100);
        assertThat(result.stopReason()).isEqualTo(ActivityProductPaginationRunner.StopReason.DONE_NO_MORE);
        assertThat(result.stillHasNextWhenStopped()).isFalse();
        assertThat(result.complete()).isTrue();
    }

    @Test
    void run_shouldStopOnRepeatedCursor() {
        ActivityProductPaginationRunner.Result result = ActivityProductPaginationRunner.run(
                request("ACT-1", 20, null),
                new ActivityProductPaginationRunner.Options(20, 300, 20_000, true),
                pageRequest -> page(1, true, "repeat-cursor"),
                page -> ActivityProductPaginationRunner.PageWriteStats.ZERO,
                page -> {
                });

        assertThat(result.pagesFetched()).isEqualTo(2);
        assertThat(result.stopReason()).isEqualTo(ActivityProductPaginationRunner.StopReason.REPEATED_CURSOR);
        assertThat(result.complete()).isFalse();
    }

    @Test
    void run_shouldStopWhenTotalIndicatesMoreButCursorIsEmpty() {
        ActivityProductPaginationRunner.Result result = ActivityProductPaginationRunner.run(
                request("ACT-1", 20, null),
                new ActivityProductPaginationRunner.Options(20, 300, 20_000, true),
                pageRequest -> new DouyinProductGateway.ActivityProductListResult(
                        false,
                        1L,
                        1L,
                        40L,
                        "",
                        products(1, 20)),
                page -> ActivityProductPaginationRunner.PageWriteStats.ZERO,
                page -> {
                });

        assertThat(result.pagesFetched()).isEqualTo(1);
        assertThat(result.stopReason()).isEqualTo(ActivityProductPaginationRunner.StopReason.EMPTY_CURSOR_WITH_HAS_NEXT);
        assertThat(result.complete()).isFalse();
    }

    @Test
    void run_shouldStopOnApiErrorAndKeepPartialSummary() {
        ActivityProductPaginationRunner.Result result = ActivityProductPaginationRunner.run(
                request("ACT-1", 20, null),
                new ActivityProductPaginationRunner.Options(20, 300, 20_000, true),
                pageRequest -> {
                    if ("20".equals(pageRequest.cursor())) {
                        throw new IllegalStateException("upstream failed");
                    }
                    return page(1, true, "20");
                },
                page -> ActivityProductPaginationRunner.PageWriteStats.ZERO,
                page -> {
                });

        assertThat(result.pagesFetched()).isEqualTo(1);
        assertThat(result.stopReason()).isEqualTo(ActivityProductPaginationRunner.StopReason.API_ERROR);
        assertThat(result.complete()).isFalse();
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("upstream failed"));
    }

    @Test
    void run_shouldStopAtMaxRowsWhenUpstreamStillHasNext() {
        ActivityProductPaginationRunner.Result result = ActivityProductPaginationRunner.run(
                request("ACT-1", 20, null),
                new ActivityProductPaginationRunner.Options(20, 300, 35, true),
                pageRequest -> {
                    int cursor = pageRequest.cursor() == null || pageRequest.cursor().isBlank()
                            ? 0
                            : Integer.parseInt(pageRequest.cursor());
                    int page = (cursor / 20) + 1;
                    return page(page, true);
                },
                page -> ActivityProductPaginationRunner.PageWriteStats.ZERO,
                page -> {
                });

        assertThat(result.pagesFetched()).isEqualTo(2);
        assertThat(result.fetchedRows()).isEqualTo(35);
        assertThat(result.stopReason()).isEqualTo(ActivityProductPaginationRunner.StopReason.MAX_ROWS_REACHED);
        assertThat(result.stillHasNextWhenStopped()).isTrue();
        assertThat(result.complete()).isFalse();
    }

    @Test
    void run_shouldCompleteWhenMaxRowsExactlyMatchesFinalPage() {
        ActivityProductPaginationRunner.Result result = ActivityProductPaginationRunner.run(
                request("ACT-1", 20, null),
                new ActivityProductPaginationRunner.Options(20, 300, 40, true),
                pageRequest -> {
                    int cursor = pageRequest.cursor() == null || pageRequest.cursor().isBlank()
                            ? 0
                            : Integer.parseInt(pageRequest.cursor());
                    int page = (cursor / 20) + 1;
                    return page(page, page < 2);
                },
                page -> ActivityProductPaginationRunner.PageWriteStats.ZERO,
                page -> {
                });

        assertThat(result.pagesFetched()).isEqualTo(2);
        assertThat(result.fetchedRows()).isEqualTo(40);
        assertThat(result.stopReason()).isEqualTo(ActivityProductPaginationRunner.StopReason.DONE_NO_MORE);
        assertThat(result.stillHasNextWhenStopped()).isFalse();
        assertThat(result.complete()).isTrue();
    }

    private DouyinProductGateway.ActivityProductQueryRequest request(String activityId, int count, String cursor) {
        return new DouyinProductGateway.ActivityProductQueryRequest(
                null, activityId, 4L, 1L, count, null, null, null, null, 1L, cursor, null);
    }

    private DouyinProductGateway.ActivityProductListResult page(int page, boolean hasNext) {
        return page(page, hasNext, hasNext ? String.valueOf(page * 20) : "");
    }

    private DouyinProductGateway.ActivityProductListResult page(int page, boolean hasNext, String nextCursor) {
        return new DouyinProductGateway.ActivityProductListResult(
                false,
                3859423L,
                1L,
                null,
                nextCursor,
                products((page - 1) * 20 + 1, 20));
    }

    private List<DouyinProductGateway.ActivityProductItem> products(int start, int count) {
        List<DouyinProductGateway.ActivityProductItem> products = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long productId = start + i;
            products.add(new DouyinProductGateway.ActivityProductItem(
                    productId,
                    "Product " + productId,
                    "",
                    100L,
                    "1.00",
                    1000L,
                    10L,
                    1000L,
                    "10%",
                    1,
                    "普通",
                    null,
                    null,
                    false,
                    true,
                    0L,
                    1L,
                    "Shop",
                    null,
                    1,
                    "推广中",
                    "category",
                    "100",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    Map.of()));
        }
        return products;
    }
}
