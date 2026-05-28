package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.logistics.LogisticsGatewayHealthResponse;
import com.colonel.saas.dto.logistics.LogisticsGatewayTestRequest;
import com.colonel.saas.dto.logistics.LogisticsGatewayTestResponse;
import com.colonel.saas.service.LogisticsGatewayHealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 物流 Gateway 诊断控制器，供运维人员检查物流 provider 的配置与联通状态。
 *
 * <ul>
 *   <li>查询当前物流 provider 的配置与联通诊断状态</li>
 *   <li>按指定 provider 发起物流查询联调测试（test 环境不请求真实 API）</li>
 * </ul>
 *
 * <p>所属业务领域：物流域 / Gateway 诊断
 * <p>API 路径前缀：{@code /admin/logistics/gateway}
 * <p>访问权限：管理员和运维人员（{@link com.colonel.saas.constant.RoleCodes#ADMIN}、{@link com.colonel.saas.constant.RoleCodes#OPS_STAFF}）
 *
 * @see com.colonel.saas.service.LogisticsGatewayHealthService
 */
@Tag(name = "物流 Gateway 诊断", description = "物流 provider 配置与联调诊断，不伪造真实联通。")
@RestController
@RequestMapping("/admin/logistics/gateway")
@RequireRoles({RoleCodes.ADMIN, RoleCodes.OPS_STAFF})
public class AdminLogisticsGatewayController extends BaseController {

    /** 物流 Gateway 健康检查服务，负责 provider 配置诊断和联通性测试 */
    private final LogisticsGatewayHealthService logisticsGatewayHealthService;

    /**
     * 构造注入物流 Gateway 健康检查服务。
     *
     * @param logisticsGatewayHealthService 物流 Gateway 健康检查服务实例
     */
    public AdminLogisticsGatewayController(LogisticsGatewayHealthService logisticsGatewayHealthService) {
        this.logisticsGatewayHealthService = logisticsGatewayHealthService;
    }

    /**
     * 查询物流 Gateway 当前状态。
     *
     * <p>处理流程：
     * <ol>
     *   <li>读取当前配置的物流 provider 信息（快递100、菜鸟等）</li>
     *   <li>诊断 provider 的联通状态（API Key 是否配置、网络是否可达等）</li>
     *   <li>返回诊断结果，包含 provider 名称、配置状态和联通检测结果</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code GET /admin/logistics/gateway/status}
     *
     * @return 物流 Gateway 健康诊断响应
     */
    @Operation(summary = "物流 Gateway 状态", description = "返回当前 provider 配置与联通诊断状态。")
    @GetMapping("/status")
    public ApiResult<LogisticsGatewayHealthResponse> status() {
        return ok(logisticsGatewayHealthService.diagnoseCurrentProvider());
    }

    /**
     * 执行物流 Gateway 联调测试。
     *
     * <p>处理流程：
     * <ol>
     *   <li>接收测试请求，包含测试用的快递单号和快递公司编码</li>
     *   <li>按当前配置的 provider 发起一次物流查询测试</li>
     *   <li>test 环境下不请求真实 API，仅验证配置连通性</li>
     *   <li>返回测试结果，包含响应时间、状态码和返回数据</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code POST /admin/logistics/gateway/test}
     *
     * @param request 联调测试请求，包含快递单号和快递公司编码
     * @return 联调测试响应，包含测试结果和耗时信息
     */
    @Operation(summary = "物流 Gateway 联调测试", description = "按 provider 发起一次物流查询测试；test 环境不请求真实 API。")
    @PostMapping("/test")
    public ApiResult<LogisticsGatewayTestResponse> test(@Valid @RequestBody LogisticsGatewayTestRequest request) {
        return ok(logisticsGatewayHealthService.testQuery(request));
    }
}
