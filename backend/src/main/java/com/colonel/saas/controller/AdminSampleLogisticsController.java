package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.service.SampleLogisticsSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "寄样物流管理", description = "管理员批量物流同步。")
@RestController
@RequestMapping("/admin/samples/logistics")
@RequireRoles({RoleCodes.ADMIN, RoleCodes.OPS_STAFF})
public class AdminSampleLogisticsController extends BaseController {

    private final SampleLogisticsSyncService sampleLogisticsSyncService;

    public AdminSampleLogisticsController(SampleLogisticsSyncService sampleLogisticsSyncService) {
        this.sampleLogisticsSyncService = sampleLogisticsSyncService;
    }

    @Operation(summary = "批量同步物流", description = "同步待发货/快递中且已有物流单号的寄样申请。")
    @PostMapping("/sync")
    public ApiResult<Map<String, Integer>> syncAll() {
        SampleLogisticsSyncService.SyncBatchSummary summary = sampleLogisticsSyncService.syncPendingInTransit(100);
        return ok(Map.of(
                "total", summary.total(),
                "success", summary.success(),
                "failed", summary.failed(),
                "skipped", summary.skipped()));
    }
}
