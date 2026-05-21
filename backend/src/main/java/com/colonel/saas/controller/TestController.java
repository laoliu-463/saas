package com.colonel.saas.controller;

import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.service.OrderSyncService;
import com.colonel.saas.service.ShortTtlCacheService;
import com.colonel.saas.testsupport.TestDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/test")
@ConditionalOnProperty(prefix = "app.test", name = "enabled", havingValue = "true")
@RequireRoles({RoleCodes.ADMIN})
@Tag(name = "测试工具", description = "仅用于测试环境的数据初始化、链路回放与状态推进工具")
public class TestController extends BaseController {

    private static final String DASHBOARD_SUMMARY_CACHE_PREFIX = "dashboard:summary:";
    private static final String DASHBOARD_METRICS_CACHE_PREFIX = "dashboard:metrics:";
    private static final String ORDER_FILTER_OPTIONS_CACHE_PREFIX = "orders:filter-options:";

    private final TestDataService testDataService;
    private final ShortTtlCacheService shortTtlCacheService;

    public TestController(TestDataService testDataService, ShortTtlCacheService shortTtlCacheService) {
        this.testDataService = testDataService;
        this.shortTtlCacheService = shortTtlCacheService;
        log.warn("TestController enabled under profile(s): local-mock/test only");
    }

    @Operation(summary = "[测试] 初始化测试数据", description = "重建测试演示所需的基础数据，不执行全量重置。适用于首次进入 test 环境或重新准备演示数据。")
    @PostMapping("/seed")
    public ApiResult<Map<String, Object>> seed() {
        Map<String, Object> result = testDataService.seedAll(false);
        evictOrderDerivedCaches();
        return ok(result);
    }

    @Operation(summary = "[测试] 重置测试数据", description = "清空并重建测试环境数据，用于恢复到标准演示初始状态。执行后会覆盖当前测试数据。")
    @PostMapping("/reset")
    public ApiResult<Map<String, Object>> reset() {
        Map<String, Object> result = testDataService.resetAll();
        evictOrderDerivedCaches();
        return ok(result);
    }

    @Operation(summary = "[测试] 触发订单同步", description = "调用测试订单同步链路，将测试环境订单数据同步到本地业务表，便于验证订单回流与归因逻辑。")
    @PostMapping("/orders/sync")
    public ApiResult<OrderSyncService.SyncResult> syncOrders() {
        OrderSyncService.SyncResult result = testDataService.syncTestOrders();
        evictOrderDerivedCaches();
        return ok(result);
    }

    @Operation(summary = "[测试] 生成已归因订单", description = "生成一笔能够命中推广映射的测试订单，用于验证已归因订单的看板、列表与详情链路。")
    @PostMapping("/orders/generate-attributed")
    public ApiResult<Map<String, Object>> generateAttributedOrder() {
        Map<String, Object> result = testDataService.generateAttributedOrder();
        evictOrderDerivedCaches();
        return ok(result);
    }

    @Operation(summary = "[测试] 生成缺少推广参数订单", description = "生成一笔未携带 pick_source 的测试订单，用于验证未归因订单诊断与异常分支。")
    @PostMapping("/orders/generate-no-pick-source")
    public ApiResult<Map<String, Object>> generateNoPickSourceOrder() {
        Map<String, Object> result = testDataService.generateNoPickSourceOrder();
        evictOrderDerivedCaches();
        return ok(result);
    }

    @Operation(summary = "[测试] 生成缺少映射订单", description = "生成一笔携带推广参数但无法命中本地映射的测试订单，用于验证映射缺失场景。")
    @PostMapping("/orders/generate-missing-mapping")
    public ApiResult<Map<String, Object>> generateMissingMappingOrder() {
        Map<String, Object> result = testDataService.generateMissingMappingOrder();
        evictOrderDerivedCaches();
        return ok(result);
    }

    @Operation(summary = "[测试] 生成多候选歧义映射订单", description = "生成一笔命中多条 native 映射的测试订单，用于验证 AMBIGUOUS_MAPPING 诊断与未归因筛选。")
    @PostMapping("/orders/generate-ambiguous-mapping")
    public ApiResult<Map<String, Object>> generateAmbiguousMappingOrder() {
        Map<String, Object> result = testDataService.generateAmbiguousMappingOrder();
        evictOrderDerivedCaches();
        return ok(result);
    }

    @Operation(summary = "[测试] 生成历史不可回填订单", description = "生成一笔 native 映射创建时间晚于订单创建时间的测试订单，用于验证 replay-attribution dryRun 的 unsafe 诊断。")
    @PostMapping("/orders/generate-history-unsafe")
    public ApiResult<Map<String, Object>> generateHistoryUnsafeOrder() {
        Map<String, Object> result = testDataService.generateHistoryUnsafeOrder();
        evictOrderDerivedCaches();
        return ok(result);
    }

    @Operation(summary = "[测试] 生成商品未覆盖订单", description = "生成一笔活动商品未覆盖的测试订单，用于验证 UPSTREAM_PRODUCT_UNCOVERED 诊断与排查链路。")
    @PostMapping("/orders/generate-product-uncovered")
    public ApiResult<Map<String, Object>> generateProductUncoveredOrder() {
        Map<String, Object> result = testDataService.generateProductUncoveredOrder();
        evictOrderDerivedCaches();
        return ok(result);
    }

    @Operation(summary = "[测试] 推进寄样到发货", description = "将指定寄样单推进到发货节点，用于验证寄样状态机与订单联动场景。")
    @PostMapping("/logistics/ship/{sampleRequestId}")
    public ApiResult<Map<String, Object>> shipSample(
            @Parameter(description = "寄样申请 ID，使用 UUID 格式。") @PathVariable UUID sampleRequestId) {
        return ok(testDataService.shipSample(sampleRequestId));
    }

    @Operation(summary = "[测试] 推进寄样到签收", description = "将指定寄样单推进到签收节点，用于验证寄样闭环、待交作业与自动完成场景。")
    @PostMapping("/logistics/sign/{sampleRequestId}")
    public ApiResult<Map<String, Object>> signSample(
            @Parameter(description = "寄样申请 ID，使用 UUID 格式。") @PathVariable UUID sampleRequestId) {
        return ok(testDataService.signSample(sampleRequestId));
    }

    private void evictOrderDerivedCaches() {
        shortTtlCacheService.evictByPrefix(DASHBOARD_SUMMARY_CACHE_PREFIX);
        shortTtlCacheService.evictByPrefix(DASHBOARD_METRICS_CACHE_PREFIX);
        shortTtlCacheService.evictByPrefix(ORDER_FILTER_OPTIONS_CACHE_PREFIX);
    }
}
