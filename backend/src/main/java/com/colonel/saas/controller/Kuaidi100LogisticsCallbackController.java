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

@RestController
@RequestMapping
@Tag(name = "物流回调")
public class Kuaidi100LogisticsCallbackController {

    private final Kuaidi100LogisticsCallbackService callbackService;

    public Kuaidi100LogisticsCallbackController(Kuaidi100LogisticsCallbackService callbackService) {
        this.callbackService = callbackService;
    }

    @Operation(summary = "接收快递100订阅推送回调", description = "快递100企业版订阅回调入口。请求为 form 表单 sign + param。")
    @PostMapping(
            value = {"/public/logistics/kuaidi100/callback", "/api/public/logistics/kuaidi100/callback"},
            consumes = MediaType.ALL_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Kuaidi100LogisticsCallbackService.CallbackAck callback(
            @RequestParam(value = "param", required = false) String param,
            @RequestParam(value = "sign", required = false) String sign) {
        if (!StringUtils.hasText(param) || !StringUtils.hasText(sign)) {
            return new Kuaidi100LogisticsCallbackService.CallbackAck(false, "500", "缺少参数");
        }
        return callbackService.handleCallback(param, sign);
    }
}
