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

/**
 * 寄样物流管理控制器，供管理员和运维人员批量同步物流信息。
 *
 * <ul>
 *   <li>批量同步待发货和快递中状态寄样的物流轨迹信息</li>
 * </ul>
 *
 * <p>所属业务领域：寄样域 / 物流同步
 * <p>API 路径前缀：{@code /admin/samples/logistics}
 * <p>访问权限：管理员和运维人员（{@link com.colonel.saas.constant.RoleCodes#ADMIN}、{@link com.colonel.saas.constant.RoleCodes#OPS_STAFF}）
 *
 * @see com.colonel.saas.service.SampleLogisticsSyncService
 */
@Tag(name = "寄样物流管理", description = "管理员批量物流同步。")
@RestController
@RequestMapping("/admin/samples/logistics")
@RequireRoles({RoleCodes.ADMIN, RoleCodes.OPS_STAFF})
public class AdminSampleLogisticsController extends BaseController {

    /** 寄样物流同步服务，负责批量拉取和更新寄样物流轨迹信息 */
    private final SampleLogisticsSyncService sampleLogisticsSyncService;

    /**
     * 构造注入寄样物流同步服务。
     *
     * @param sampleLogisticsSyncService 寄样物流同步服务实例
     */
    public AdminSampleLogisticsController(SampleLogisticsSyncService sampleLogisticsSyncService) {
        this.sampleLogisticsSyncService = sampleLogisticsSyncService;
    }

    /**
     * 批量同步寄样物流信息。
     *
     * <p>处理流程：
     * <ol>
     *   <li>查询所有待发货/快递中且已有物流单号的寄样申请</li>
     *   <li>逐条调用物流查询接口同步物流轨迹信息</li>
     *   <li>汇总同步结果，包含总数、成功数、失败数和跳过数</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code POST /admin/samples/logistics/sync}
     *
     * @return 同步结果汇总，包含 total（总数）、success（成功数）、failed（失败数）、skipped（跳过数）
     */
    @Operation(summary = "批量同步物流", description = "同步待发货/快递中且已有物流单号的寄样申请。")
    @PostMapping("/sync")
    public ApiResult<Map<String, Integer>> syncAll() {
        // 第一步：批量同步物流轨迹，每批最多 100 条
        SampleLogisticsSyncService.SyncBatchSummary summary = sampleLogisticsSyncService.syncPendingInTransit(100);
        // 第二步：返回同步汇总结果
        return ok(Map.of(
                "total", summary.total(),
                "success", summary.success(),
                "failed", summary.failed(),
                "skipped", summary.skipped()));
    }
}
