package com.colonel.saas.controller;

import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.testsupport.TestDataService;
import com.colonel.saas.service.OrderSyncService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/test")
@ConditionalOnProperty(prefix = "app.test", name = "enabled", havingValue = "true")
public class TestController extends BaseController {

    private final TestDataService testDataService;

    public TestController(TestDataService testDataService) {
        this.testDataService = testDataService;
    }

    @PostMapping("/seed")
    public ApiResult<Map<String, Object>> seed() {
        return ok(testDataService.seedAll(false));
    }

    @PostMapping("/reset")
    public ApiResult<Map<String, Object>> reset() {
        return ok(testDataService.resetAll());
    }

    @PostMapping("/orders/sync")
    public ApiResult<OrderSyncService.SyncResult> syncOrders() {
        return ok(testDataService.syncTestOrders());
    }

    @PostMapping("/orders/generate-attributed")
    public ApiResult<Map<String, Object>> generateAttributedOrder() {
        return ok(testDataService.generateAttributedOrder());
    }

    @PostMapping("/orders/generate-no-pick-source")
    public ApiResult<Map<String, Object>> generateNoPickSourceOrder() {
        return ok(testDataService.generateNoPickSourceOrder());
    }

    @PostMapping("/orders/generate-missing-mapping")
    public ApiResult<Map<String, Object>> generateMissingMappingOrder() {
        return ok(testDataService.generateMissingMappingOrder());
    }

    @PostMapping("/logistics/ship/{sampleRequestId}")
    public ApiResult<Map<String, Object>> shipSample(@PathVariable UUID sampleRequestId) {
        return ok(testDataService.shipSample(sampleRequestId));
    }

    @PostMapping("/logistics/sign/{sampleRequestId}")
    public ApiResult<Map<String, Object>> signSample(@PathVariable UUID sampleRequestId) {
        return ok(testDataService.signSample(sampleRequestId));
    }
}

