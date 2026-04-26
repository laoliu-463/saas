package com.colonel.saas.controller;

import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.mock.LocalMockDataService;
import com.colonel.saas.service.OrderSyncService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/mock")
@ConditionalOnProperty(prefix = "app.mock", name = "enabled", havingValue = "true")
public class MockController extends BaseController {

    private final LocalMockDataService localMockDataService;

    public MockController(LocalMockDataService localMockDataService) {
        this.localMockDataService = localMockDataService;
    }

    @PostMapping("/seed")
    public ApiResult<Map<String, Object>> seed() {
        return ok(localMockDataService.seedAll(false));
    }

    @PostMapping("/reset")
    public ApiResult<Map<String, Object>> reset() {
        return ok(localMockDataService.resetAll());
    }

    @PostMapping("/orders/sync")
    public ApiResult<OrderSyncService.SyncResult> syncOrders() {
        return ok(localMockDataService.syncMockOrders());
    }

    @PostMapping("/orders/generate-attributed")
    public ApiResult<Map<String, Object>> generateAttributedOrder() {
        return ok(localMockDataService.generateAttributedOrder());
    }

    @PostMapping("/orders/generate-no-pick-source")
    public ApiResult<Map<String, Object>> generateNoPickSourceOrder() {
        return ok(localMockDataService.generateNoPickSourceOrder());
    }

    @PostMapping("/orders/generate-missing-mapping")
    public ApiResult<Map<String, Object>> generateMissingMappingOrder() {
        return ok(localMockDataService.generateMissingMappingOrder());
    }

    @PostMapping("/logistics/ship/{sampleRequestId}")
    public ApiResult<Map<String, Object>> shipSample(@PathVariable UUID sampleRequestId) {
        return ok(localMockDataService.shipSample(sampleRequestId));
    }

    @PostMapping("/logistics/sign/{sampleRequestId}")
    public ApiResult<Map<String, Object>> signSample(@PathVariable UUID sampleRequestId) {
        return ok(localMockDataService.signSample(sampleRequestId));
    }
}
