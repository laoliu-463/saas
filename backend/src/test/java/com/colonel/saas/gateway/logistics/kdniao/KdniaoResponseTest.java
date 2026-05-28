package com.colonel.saas.gateway.logistics.kdniao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KdniaoResponse 单元测试
 */
class KdniaoResponseTest {

    @Nested
    @DisplayName("LogisticsState 枚举测试")
    class LogisticsStateTest {

        @Test
        void fromCode_shouldReturnInTransitForCode2() {
            KdniaoResponse.LogisticsState state = KdniaoResponse.LogisticsState.fromCode("2");
            assertThat(state).isEqualTo(KdniaoResponse.LogisticsState.IN_TRANSIT);
            assertThat(state.getCode()).isEqualTo("2");
            assertThat(state.getStatus()).isEqualTo("IN_TRANSIT");
        }

        @Test
        void fromCode_shouldReturnSignedForCode3() {
            KdniaoResponse.LogisticsState state = KdniaoResponse.LogisticsState.fromCode("3");
            assertThat(state).isEqualTo(KdniaoResponse.LogisticsState.SIGNED);
            assertThat(state.getCode()).isEqualTo("3");
            assertThat(state.getStatus()).isEqualTo("SIGNED");
        }

        @Test
        void fromCode_shouldReturnExceptionForCode4() {
            KdniaoResponse.LogisticsState state = KdniaoResponse.LogisticsState.fromCode("4");
            assertThat(state).isEqualTo(KdniaoResponse.LogisticsState.EXCEPTION);
            assertThat(state.getCode()).isEqualTo("4");
            assertThat(state.getStatus()).isEqualTo("EXCEPTION");
        }

        @Test
        void fromCode_shouldReturnNullForUnknownCode() {
            assertThat(KdniaoResponse.LogisticsState.fromCode("0")).isNull();
            assertThat(KdniaoResponse.LogisticsState.fromCode("1")).isNull();
            assertThat(KdniaoResponse.LogisticsState.fromCode("5")).isNull();
            assertThat(KdniaoResponse.LogisticsState.fromCode("6")).isNull();
            assertThat(KdniaoResponse.LogisticsState.fromCode("unknown")).isNull();
        }

        @Test
        void fromCode_shouldReturnNullForNullCode() {
            assertThat(KdniaoResponse.LogisticsState.fromCode(null)).isNull();
        }

        @Test
        void values_shouldHaveThreeStates() {
            KdniaoResponse.LogisticsState[] values = KdniaoResponse.LogisticsState.values();
            assertThat(values).hasSize(3);
        }
    }

    @Nested
    @DisplayName("LogisticsTrace 内部类测试")
    class LogisticsTraceTest {

        @Test
        void getterSetter() {
            KdniaoResponse.LogisticsTrace trace = new KdniaoResponse.LogisticsTrace();
            trace.setAcceptTime("2026/05/15 10:30:00");
            trace.setAcceptStation("【深圳市】已签收");
            trace.setRemark("签收备注");

            assertThat(trace.getAcceptTime()).isEqualTo("2026/05/15 10:30:00");
            assertThat(trace.getAcceptStation()).isEqualTo("【深圳市】已签收");
            assertThat(trace.getRemark()).isEqualTo("签收备注");
        }

        @Test
        void shouldAllowNullFields() {
            KdniaoResponse.LogisticsTrace trace = new KdniaoResponse.LogisticsTrace();

            assertThat(trace.getAcceptTime()).isNull();
            assertThat(trace.getAcceptStation()).isNull();
            assertThat(trace.getRemark()).isNull();
        }
    }

    @Nested
    @DisplayName("KdniaoResponse 主体类测试")
    class KdniaoResponseBodyTest {

        @Test
        void getterSetter() {
            KdniaoResponse response = new KdniaoResponse();
            response.setEBusinessId("test-business");
            response.setOrderCode("ORDER123");
            response.setShipperCode("SF");
            response.setLogisticCode("SF001");
            response.setSuccess(true);
            response.setReason(null);
            response.setState("3");

            assertThat(response.getEBusinessId()).isEqualTo("test-business");
            assertThat(response.getOrderCode()).isEqualTo("ORDER123");
            assertThat(response.getShipperCode()).isEqualTo("SF");
            assertThat(response.getLogisticCode()).isEqualTo("SF001");
            assertThat(response.getSuccess()).isTrue();
            assertThat(response.getReason()).isNull();
            assertThat(response.getState()).isEqualTo("3");
        }

        @Test
        void shouldAllowNullFields() {
            KdniaoResponse response = new KdniaoResponse();

            assertThat(response.getEBusinessId()).isNull();
            assertThat(response.getOrderCode()).isNull();
            assertThat(response.getShipperCode()).isNull();
            assertThat(response.getLogisticCode()).isNull();
            assertThat(response.getSuccess()).isNull();
            assertThat(response.getReason()).isNull();
            assertThat(response.getState()).isNull();
            assertThat(response.getTraces()).isNull();
        }

        @Test
        void shouldSetAndGetTraces() {
            KdniaoResponse.LogisticsTrace trace1 = new KdniaoResponse.LogisticsTrace();
            trace1.setAcceptTime("2026/05/14 10:00:00");
            trace1.setAcceptStation("已揽收");

            KdniaoResponse.LogisticsTrace trace2 = new KdniaoResponse.LogisticsTrace();
            trace2.setAcceptTime("2026/05/15 14:00:00");
            trace2.setAcceptStation("已签收");

            KdniaoResponse response = new KdniaoResponse();
            response.setTraces(List.of(trace1, trace2));

            assertThat(response.getTraces()).hasSize(2);
            assertThat(response.getTraces().get(0).getAcceptStation()).isEqualTo("已揽收");
            assertThat(response.getTraces().get(1).getAcceptStation()).isEqualTo("已签收");
        }

        @Test
        void shouldSupportEmptyTraces() {
            KdniaoResponse response = new KdniaoResponse();
            response.setTraces(List.of());

            assertThat(response.getTraces()).isEmpty();
        }

        @Test
        void shouldHandleSuccessFalse() {
            KdniaoResponse response = new KdniaoResponse();
            response.setSuccess(false);
            response.setReason("暂无物流信息");

            assertThat(response.getSuccess()).isFalse();
            assertThat(response.getReason()).isEqualTo("暂无物流信息");
        }
    }
}
