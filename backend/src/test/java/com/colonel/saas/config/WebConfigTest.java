package com.colonel.saas.config;

import com.colonel.saas.security.JwtAuthInterceptor;
import com.colonel.saas.security.OperationLogInterceptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class WebConfigTest {

    @Test
    void constructor_shouldRejectWildcardCorsPatternWhenCredentialsAreEnabled() {
        assertThatThrownBy(() -> new WebConfig(
                mock(JwtAuthInterceptor.class),
                mock(OperationLogInterceptor.class),
                "*"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CORS");
    }

    @Test
    void constructor_shouldAllowLocalhostWildcardPorts() {
        assertThatCode(() -> new WebConfig(
                mock(JwtAuthInterceptor.class),
                mock(OperationLogInterceptor.class),
                "http://localhost:*, http://127.0.0.1:*"
        )).doesNotThrowAnyException();
    }
}
