package com.colonel.saas.domain.colonel.application;

import com.colonel.saas.domain.colonel.application.port.ColonelActivityDetailPort;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ColonelActivityDetailQueryServiceTest {

    @Test
    void getDouyinDetail_shouldReturnPortResponseUnchanged() {
        Map<String, Object> expected = Map.of(
                "code", 0,
                "message", "ok",
                "data", Map.of("activityId", "ACT-1"));
        ColonelActivityDetailQueryService service = new ColonelActivityDetailQueryService(
                new StubColonelActivityDetailPort(expected));

        Map<String, Object> actual = service.getDouyinDetail("app-1", "ACT-1");

        assertThat(actual).isSameAs(expected);
    }

    private record StubColonelActivityDetailPort(Map<String, Object> response)
            implements ColonelActivityDetailPort {

        @Override
        public Map<String, Object> getDouyinDetail(String appId, String activityId) {
            return response;
        }
    }
}
