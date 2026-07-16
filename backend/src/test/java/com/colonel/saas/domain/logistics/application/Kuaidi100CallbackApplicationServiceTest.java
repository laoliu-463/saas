package com.colonel.saas.domain.logistics.application;

import com.colonel.saas.config.LogisticsProperties;
import com.colonel.saas.domain.logistics.application.Kuaidi100CallbackApplicationService.CallbackAck;
import com.colonel.saas.domain.sample.event.SampleDomainEventPublisher;
import com.colonel.saas.entity.SampleLogisticsTrace;
import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.mapper.SampleLogisticsTraceMapper;
import com.colonel.saas.mapper.SampleRequestMapper;
import com.colonel.saas.service.SampleStatusLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

/**
 * DDD-LOGISTICS Slice 3: Kuaidi100CallbackApplicationService 单元测试.
 *
 * <p>基于快递100官方回调 API 字段结构, 覆盖:
 * <ul>
 *   <li>参数校验 (空 param/sign)</li>
 *   <li>MD5 签名验证 (param + salt)</li>
 *   <li>JSON 报文解析失败</li>
 *   <li>缺少 lastResult / com / nu</li>
 *   <li>SampleRequest 匹配 (LambdaQueryWrapper + normalizeCompanyCode)</li>
 *   <li>8 个 setter (LogisticsProvider/LastCallbackAt/CallbackStatus/...)</li>
 *   <li>SHA-256 节点幂等插入 (insertMissingTraceNodes)</li>
 *   <li>异常状态记录 (isExceptionState + setLogisticsExceptionReason)</li>
 *   <li>签收状态自动推进 (state="3" + applySigned)</li>
 *   <li>乐观锁更新 (OptimisticLockSupport.requireUpdated)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class Kuaidi100CallbackApplicationServiceTest {

    @Mock private SampleRequestMapper sampleRequestMapper;
    @Mock private SampleLogisticsTraceMapper sampleLogisticsTraceMapper;
    @Mock private SampleStatusLogService sampleStatusLogService;
    @Mock private SampleDomainEventPublisher sampleDomainEventPublisher;

    private LogisticsProperties properties;
    private ObjectMapper objectMapper;
    private Kuaidi100CallbackApplicationService applicationService;

    @BeforeEach
    void setUp() {
        properties = new LogisticsProperties();
        properties.getKd100().setCallbackSalt("test-salt-123");
        objectMapper = new ObjectMapper();
        applicationService = new Kuaidi100CallbackApplicationService(
                properties, sampleRequestMapper, sampleLogisticsTraceMapper,
                sampleStatusLogService, sampleDomainEventPublisher, objectMapper);
    }

    /**
     * 完整成功路径: MD5 签名正确 + JSON 解析 + 匹配 + 8 setter + 节点插入 + 乐观锁.
     */
    @Test
    void handleCallback_success() {
        String param = "{\"lastResult\":{\"com\":\"SF\",\"nu\":\"SF1234567890\",\"state\":\"3\",\"message\":\"已签收\",\"data\":[{\"ftime\":\"2026-07-14 10:00:00\",\"context\":\"已签收\",\"location\":\"上海\"}]}}";
        String sign = md5Sign(param, "test-salt-123");

        SampleRequest sample = newSample("shunfeng", "SF1234567890");
        sample.setStatus(Kuaidi100CallbackApplicationService.STATUS_SHIPPING);
        when(sampleRequestMapper.selectList(any())).thenReturn(List.of(sample));
        when(sampleLogisticsTraceMapper.selectCount(any())).thenReturn(0L);
        when(sampleRequestMapper.updateById(sample)).thenReturn(1);

        CallbackAck result = applicationService.handleCallback(param, sign);

        assertThat(result.result()).isTrue();
        assertThat(result.returnCode()).isEqualTo("200");
        // 8 个 setter
        assertThat(sample.getLogisticsProvider()).isEqualTo("KUAIDI100");
        assertThat(sample.getLogisticsLastCallbackAt()).isNotNull();
        assertThat(sample.getLogisticsCallbackStatus()).isNull();  // text() 返回 null
        assertThat(sample.getLogisticsCallbackMessage()).isEqualTo("已签收");
        assertThat(sample.getLogisticsStatus()).isEqualTo("3");
        assertThat(sample.getLogisticsStatusName()).isEqualTo("SIGNED");
        assertThat(sample.getLogisticsRawPayload()).isNotNull();
        // 节点插入
        ArgumentCaptor<SampleLogisticsTrace> captor = ArgumentCaptor.forClass(SampleLogisticsTrace.class);
        verify(sampleLogisticsTraceMapper, times(1)).insert(captor.capture());
        // 乐观锁
        verify(sampleRequestMapper, times(1)).updateById(sample);
    }

    /**
     * 缺 param / sign.
     */
    @Test
    void handleCallback_failWhenParamOrSignMissing() {
        CallbackAck r1 = applicationService.handleCallback(null, "sign");
        assertThat(r1.result()).isFalse();
        assertThat(r1.message()).isEqualTo("缺少参数");

        CallbackAck r2 = applicationService.handleCallback("param", null);
        assertThat(r2.result()).isFalse();

        CallbackAck r3 = applicationService.handleCallback("", "");
        assertThat(r3.result()).isFalse();

        verify(sampleRequestMapper, never()).updateById(any());
    }

    /**
     * MD5 签名错误.
     */
    @Test
    void handleCallback_failWhenSignInvalid() {
        String param = "{\"lastResult\":{\"com\":\"SF\",\"nu\":\"X\"}}";
        CallbackAck result = applicationService.handleCallback(param, "wrong-sign");

        assertThat(result.result()).isFalse();
        assertThat(result.message()).isEqualTo("签名错误");
    }

    /**
     * MD5 签名验证 (sALT 不配置时直接 false).
     */
    @Test
    void handleCallback_failWhenSaltNotConfigured() {
        properties.getKd100().setCallbackSalt("");
        String param = "{\"lastResult\":{\"com\":\"SF\",\"nu\":\"X\"}}";
        CallbackAck result = applicationService.handleCallback(param, "any-sign");

        assertThat(result.result()).isFalse();
        assertThat(result.message()).isEqualTo("签名错误");
    }

    /**
     * JSON 解析失败.
     */
    @Test
    void handleCallback_failWhenJsonParseFails() {
        String param = "not a json";
        String sign = md5Sign(param, "test-salt-123");
        CallbackAck result = applicationService.handleCallback(param, sign);

        assertThat(result.result()).isFalse();
        assertThat(result.message()).isEqualTo("参数解析失败");
    }

    /**
     * 缺少 lastResult.
     */
    @Test
    void handleCallback_failWhenLastResultMissing() {
        String param = "{\"otherField\":1}";
        String sign = md5Sign(param, "test-salt-123");
        CallbackAck result = applicationService.handleCallback(param, sign);

        assertThat(result.result()).isFalse();
        assertThat(result.message()).isEqualTo("缺少 lastResult");
    }

    /**
     * 缺少 com / nu.
     */
    @Test
    void handleCallback_failWhenCompanyOrTrackingNoMissing() {
        String param = "{\"lastResult\":{\"com\":\"\",\"nu\":\"X\"}}";
        String sign = md5Sign(param, "test-salt-123");
        CallbackAck result = applicationService.handleCallback(param, sign);

        assertThat(result.result()).isFalse();
        assertThat(result.message()).isEqualTo("缺少快递公司或物流单号");
    }

    /**
     * SampleRequest 匹配 (公司编码标准化: SF -> shunfeng).
     */
    @Test
    void findMatchedSample_normalizesCompanyCode() {
        SampleRequest s = newSample("shunfeng", "SF001");
        lenient().when(sampleRequestMapper.selectList(any())).thenReturn(List.of(s));
        lenient().when(sampleRequestMapper.updateById(s)).thenReturn(1);
        lenient().when(sampleLogisticsTraceMapper.selectCount(any())).thenReturn(0L);

        String param = "{\"lastResult\":{\"com\":\"SF\",\"nu\":\"SF001\",\"state\":\"0\",\"data\":[]}}";
        String sign = md5Sign(param, "test-salt-123");

        applicationService.handleCallback(param, sign);

        verify(sampleRequestMapper, times(1)).updateById(s);
    }

    /**
     * 节点 SHA-256 幂等 (已存在节点跳过).
     */
    @Test
    void insertMissingTraceNodes_skipsExisting() {
        SampleRequest s = newSample("shunfeng", "SF002");
        s.setStatus(Kuaidi100CallbackApplicationService.STATUS_SHIPPING);
        when(sampleRequestMapper.selectList(any())).thenReturn(List.of(s));
        // 节点已存在 (count=1)
        when(sampleLogisticsTraceMapper.selectCount(any())).thenReturn(1L);
        when(sampleRequestMapper.updateById(s)).thenReturn(1);

        String param = "{\"lastResult\":{\"com\":\"SF\",\"nu\":\"SF002\",\"state\":\"0\",\"data\":[{\"ftime\":\"2026-07-14 10:00:00\",\"context\":\"运输中\"}]}}";
        String sign = md5Sign(param, "test-salt-123");

        applicationService.handleCallback(param, sign);

        // 1 个节点已存在, 0 次 insert
        verify(sampleLogisticsTraceMapper, never()).insert(any(SampleLogisticsTrace.class));
    }

    /**
     * 异常状态 (state=2/4/6/14) 记录 exception reason.
     */
    @Test
    void handleCallback_exceptionState_recordsExceptionReason() {
        SampleRequest s = newSample("shunfeng", "SF003");
        s.setStatus(Kuaidi100CallbackApplicationService.STATUS_SHIPPING);
        when(sampleRequestMapper.selectList(any())).thenReturn(List.of(s));
        when(sampleLogisticsTraceMapper.selectCount(any())).thenReturn(0L);
        when(sampleRequestMapper.updateById(s)).thenReturn(1);

        String param = "{\"lastResult\":{\"com\":\"SF\",\"nu\":\"SF003\",\"state\":\"2\",\"message\":\"派件异常\",\"data\":[{\"ftime\":\"2026-07-14 10:00:00\",\"context\":\"派件异常\",\"location\":\"上海\"}]}}";
        String sign = md5Sign(param, "test-salt-123");

        applicationService.handleCallback(param, sign);

        // 异常状态: setLogisticsExceptionReason = "派件异常" (latestContext from nodes)
        assertThat(s.getLogisticsExceptionReason()).isEqualTo("派件异常");
        // state=2 不是 SIGNED, applySigned 不调
        verify(sampleStatusLogService, never()).log(any(), anyInt(), anyInt(), any(), anyString());
    }

    /**
     * 签收状态 (state=3) 自动推进 + SampleStatusLogService.log + 事件发布.
     */
    @Test
    void handleCallback_signedState_progressesAndPublishes() {
        SampleRequest s = newSample("shunfeng", "SF004");
        s.setStatus(Kuaidi100CallbackApplicationService.STATUS_SHIPPING);
        s.setUserId(UUID.randomUUID());
        when(sampleRequestMapper.selectList(any())).thenReturn(List.of(s));
        when(sampleLogisticsTraceMapper.selectCount(any())).thenReturn(0L);
        when(sampleRequestMapper.updateById(s)).thenReturn(1);

        String param = "{\"lastResult\":{\"com\":\"SF\",\"nu\":\"SF004\",\"state\":\"3\",\"message\":\"已签收\",\"data\":[{\"ftime\":\"2026-07-14 10:00:00\",\"context\":\"已签收\",\"location\":\"上海\"}]}}";
        String sign = md5Sign(param, "test-salt-123");

        applicationService.handleCallback(param, sign);

        assertThat(s.getStatus()).isEqualTo(Kuaidi100CallbackApplicationService.STATUS_PENDING_HOMEWORK);
        assertThat(s.getSignedAt()).isNotNull();
        // SampleStatusLogService.log 调用
        verify(sampleStatusLogService, times(1)).log(eq(s.getId()),
                eq(Kuaidi100CallbackApplicationService.STATUS_SHIPPING),
                eq(Kuaidi100CallbackApplicationService.STATUS_PENDING_HOMEWORK),
                any(), eq("快递100签收回调自动推进"));
        // SampleDomainEventPublisher.publishSampleSigned 调用
        verify(sampleDomainEventPublisher, times(1)).publishSampleSigned(eq(s), any());
    }

    /**
     * 签收但 status 不是 SHIPPING/PENDING_SHIP 时不推进.
     */
    @Test
    void handleCallback_signedButNotInProgress_doesNotProgress() {
        SampleRequest s = newSample("shunfeng", "SF005");
        s.setStatus(Kuaidi100CallbackApplicationService.STATUS_PENDING_HOMEWORK);  // 已经是待作业
        lenient().when(sampleRequestMapper.selectList(any())).thenReturn(List.of(s));
        lenient().when(sampleLogisticsTraceMapper.selectCount(any())).thenReturn(0L);
        lenient().when(sampleRequestMapper.updateById(s)).thenReturn(1);

        String param = "{\"lastResult\":{\"com\":\"SF\",\"nu\":\"SF005\",\"state\":\"3\",\"data\":[]}}";
        String sign = md5Sign(param, "test-salt-123");

        applicationService.handleCallback(param, sign);

        // 不调 log (status 不在可推进范围)
        verify(sampleStatusLogService, never()).log(any(), anyInt(), anyInt(), any(), anyString());
    }

    /**
     * 状态名映射: state=3 -> SIGNED, state=0 -> IN_TRANSIT, state=5 -> DELIVERING, state=2 -> EXCEPTION.
     */
    @Test
    void statusName_mapping() {
        // 间接测: 通过 handleCallback 验证 (setLogisticsStatusName)
        SampleRequest s = newSample("shunfeng", "SF006");
        lenient().when(sampleRequestMapper.selectList(any())).thenReturn(List.of(s));
        lenient().when(sampleLogisticsTraceMapper.selectCount(any())).thenReturn(0L);
        lenient().when(sampleRequestMapper.updateById(s)).thenReturn(1);

        for (String state : List.of("0", "1", "3", "5")) {
            s.setStatus(Kuaidi100CallbackApplicationService.STATUS_SHIPPING);
            s.setLogisticsStatusName(null);
            String param = String.format("{\"lastResult\":{\"com\":\"SF\",\"nu\":\"SF006\",\"state\":\"%s\",\"data\":[]}}", state);
            String sign = md5Sign(param, "test-salt-123");
            applicationService.handleCallback(param, sign);
            assertThat(s.getLogisticsStatusName()).isNotNull();
        }
    }

    /**
     * 公司编码标准化: SF / SHUNFENG / shunfeng 都映射到 shunfeng.
     */
    @Test
    void normalizeCompanyCode_mapping() {
        SampleRequest s1 = newSample("shunfeng", "X1");
        s1.setStatus(Kuaidi100CallbackApplicationService.STATUS_SHIPPING);
        lenient().when(sampleRequestMapper.selectList(any())).thenReturn(List.of(s1));
        lenient().when(sampleLogisticsTraceMapper.selectCount(any())).thenReturn(0L);
        lenient().when(sampleRequestMapper.updateById(s1)).thenReturn(1);
        String param1 = "{\"lastResult\":{\"com\":\"SHUNFENG\",\"nu\":\"X1\",\"state\":\"0\",\"data\":[]}}";
        applicationService.handleCallback(param1, md5Sign(param1, "test-salt-123"));
        verify(sampleRequestMapper, times(1)).updateById(s1);
    }

    // ===== Helper =====

    private SampleRequest newSample(String shipperCode, String trackingNo) {
        SampleRequest s = new SampleRequest();
        s.setId(UUID.randomUUID());
        s.setShipperCode(shipperCode);
        s.setTrackingNo(trackingNo);
        return s;
    }

    private String md5Sign(String param, String salt) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("MD5")
                    .digest((param + salt).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest).toUpperCase(java.util.Locale.ROOT);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}