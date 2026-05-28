package com.colonel.saas.controller;

import com.colonel.saas.service.Kuaidi100LogisticsCallbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 快递100物流回调控制器，接收快递100企业版订阅推送的物流状态变更回调。
 *
 * <ul>
 *   <li>接收快递100订阅推送回调：解析 form 表单中的 param 和 sign 参数，转发至回调服务处理</li>
 *   <li>校验回调参数完整性：缺失 param 或 sign 时直接返回错误应答</li>
 * </ul>
 *
 * <p>所属业务领域：物流域 / 快递100回调
 * <p>API 路径前缀：{@code /public/logistics/kuaidi100/callback} 或 {@code /api/public/logistics/kuaidi100/callback}
 * <p>访问权限：公开接口，无需认证（由快递100服务器直接调用）
 *
 * @see com.colonel.saas.service.Kuaidi100LogisticsCallbackService
 */
@RestController
@RequestMapping
@Tag(name = "物流回调")
public class Kuaidi100LogisticsCallbackController {

    /** 快递100物流回调服务，负责解析加密参数、验证签名并更新物流状态 */
    private final Kuaidi100LogisticsCallbackService callbackService;

    /**
     * 构造注入物流回调服务。
     *
     * @param callbackService 物流回调服务实例
     */
    public Kuaidi100LogisticsCallbackController(Kuaidi100LogisticsCallbackService callbackService) {
        this.callbackService = callbackService;
    }

    /**
     * 接收快递100订阅推送回调。
     *
     * <p>处理流程：
     * <ol>
     *   <li>校验请求参数 param 和 sign 是否均非空</li>
     *   <li>参数缺失时直接返回失败应答（错误码 500，消息"缺少参数"）</li>
     *   <li>将 param 和 sign 交由回调服务处理，回调服务内部完成解密、签名验证和物流状态更新</li>
     *   <li>返回快递100要求的应答格式 {@link Kuaidi100LogisticsCallbackService.CallbackAck}</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code POST /public/logistics/kuaidi100/callback} 或 {@code POST /api/public/logistics/kuaidi100/callback}
     * <p>请求格式：{@code application/x-www-form-urlencoded} 或任意格式（consumes = ALL）
     *
     * @param param 快递100回调参数（加密的物流状态 JSON），可为空
     * @param sign  快递100回调签名，用于验证请求合法性，可为空
     * @return 快递100要求的应答对象 {@link Kuaidi100LogisticsCallbackService.CallbackAck}，包含成功/失败标识、应答码和消息
     */
    @Operation(summary = "接收快递100订阅推送回调", description = "快递100企业版订阅回调入口。请求为 form 表单 sign + param。")
    @PostMapping(
            value = {"/public/logistics/kuaidi100/callback", "/api/public/logistics/kuaidi100/callback"},
            consumes = MediaType.ALL_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Kuaidi100LogisticsCallbackService.CallbackAck callback(
            @RequestParam(value = "param", required = false) String param,
            @RequestParam(value = "sign", required = false) String sign) {
        // 第一步：校验回调参数完整性
        if (!StringUtils.hasText(param) || !StringUtils.hasText(sign)) {
            return new Kuaidi100LogisticsCallbackService.CallbackAck(false, "500", "缺少参数");
        }
        // 第二步：将参数交由回调服务处理（解密、验签、更新物流状态）
        return callbackService.handleCallback(param, sign);
    }
}
