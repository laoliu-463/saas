package com.colonel.saas.controller;

import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.service.OrderSyncService;
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

    private final TestDataService testDataService;

    public TestController(TestDataService testDataService) {
        this.testDataService = testDataService;
        log.warn("TestController enabled under profile(s): local-mock/test only");
    }

    @Operation(summary = "[测试] 初始化测试数据", description = "重建测试演示所需的基础数据，不执行全量重置。适用于首次进入 test 环境或重新准备演示数据。")
    @PostMapping("/seed")
    public ApiResult<Map<String, Object>> seed() {
        return ok(testDataService.seedAll(false));
    }

    @Operation(summary = "[测试] 重置测试数据", description = "清空并重建测试环境数据，用于恢复到标准演示初始状态。执行后会覆盖当前测试数据。")
    @PostMapping("/reset")
    public ApiResult<Map<String, Object>> reset() {
        return ok(testDataService.resetAll());
    }

    @Operation(summary = "[测试] 触发订单同步", description = "调用测试订单同步链路，将测试环境订单数据同步到本地业务表，便于验证订单回流与归因逻辑。")
    @PostMapping("/orders/sync")
    public ApiResult<OrderSyncService.SyncResult> syncOrders() {
        return ok(testDataService.syncTestOrders());
    }

    @Operation(summary = "[测试] 生成已归因订单", description = "生成一笔能够命中推广映射的测试订单，用于验证已归因订单的看板、列表与详情链路。")
    @PostMapping("/orders/generate-attributed")
    public ApiResult<Map<String, Object>> generateAttributedOrder() {
        return ok(testDataService.generateAttributedOrder());
    }

    @Operation(summary = "[测试] 生成缺少推广参数订单", description = "生成一笔未携带 pick_source 的测试订单，用于验证未归因订单诊断与异常分支。")
    @PostMapping("/orders/generate-no-pick-source")
    public ApiResult<Map<String, Object>> generateNoPickSourceOrder() {
        return ok(testDataService.generateNoPickSourceOrder());
    }

    @Operation(summary = "[测试] 生成缺少映射订单", description = "生成一笔携带推广参数但无法命中本地映射的测试订单，用于验证映射缺失场景。")
    @PostMapping("/orders/generate-missing-mapping")
    public ApiResult<Map<String, Object>> generateMissingMappingOrder() {
        return ok(testDataService.generateMissingMappingOrder());
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
}
