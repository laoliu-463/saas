package com.colonel.saas.service;

import com.colonel.saas.config.LogisticsProperties;
import com.colonel.saas.domain.logistics.application.Kuaidi100CallbackApplicationService;
import com.colonel.saas.domain.logistics.application.Kuaidi100CallbackApplicationService.CallbackAck;
import com.colonel.saas.domain.sample.event.SampleDomainEventPublisher;
import com.colonel.saas.mapper.SampleLogisticsTraceMapper;
import com.colonel.saas.mapper.SampleRequestMapper;
import com.colonel.saas.service.SampleStatusLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * 快递100物流轨迹回调服务 (DDD-LOGISTICS Slice 3 委派壳).
 *
 * <p>本类为 1-line 委派壳, 所有真实业务逻辑已搬至
 * {@link Kuaidi100CallbackApplicationService}. 现有调用方
 * ({@code Kuaidi100LogisticsCallbackController}) 继续通过本类调用, 行为零变化.</p>
 *
 * <p>业务字段基于快递100官方回调 API 文档 (POST callback form: param + sign,
 * 签名 MD5(param + salt), JSON 报文: lastResult.com/nu/state/data[]).</p>
 */
@Service
public class Kuaidi100LogisticsCallbackService {

    private final Kuaidi100CallbackApplicationService applicationService;

    public Kuaidi100LogisticsCallbackService(
            LogisticsProperties properties,
            SampleRequestMapper sampleRequestMapper,
            SampleLogisticsTraceMapper sampleLogisticsTraceMapper,
            SampleStatusLogService sampleStatusLogService,
            SampleDomainEventPublisher sampleDomainEventPublisher,
            ObjectMapper objectMapper) {
        this.applicationService = new Kuaidi100CallbackApplicationService(
                properties,
                sampleRequestMapper,
                sampleLogisticsTraceMapper,
                sampleStatusLogService,
                sampleDomainEventPublisher,
                objectMapper);
    }

    /**
     * 处理快递100回调 (委派壳, 1-line delegate).
     */
    public CallbackAck handleCallback(String param, String sign) {
        return applicationService.handleCallback(param, sign);
    }
}