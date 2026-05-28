package com.colonel.saas.config;

import com.colonel.saas.security.OperationLogInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class WebConfigTest {

    @Test
    void constructor_shouldRejectWildcardCorsPatternWhenCredentialsAreEnabled() {
        assertThatThrownBy(() -> new WebConfig(
                mock(OperationLogInterceptor.class),
                "*"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CORS");
    }

    @Test
    void constructor_shouldAllowLocalhostWildcardPorts() {
        assertThatCode(() -> new WebConfig(
                mock(OperationLogInterceptor.class),
                "http://localhost:*, http://127.0.0.1:*"
        )).doesNotThrowAnyException();
    }

    @Test
    void addInterceptors_shouldRegisterOnlyOperationLogInterceptor() {
        OperationLogInterceptor operationLogInterceptor = mock(OperationLogInterceptor.class);
        InterceptorRegistry registry = mock(InterceptorRegistry.class);
        InterceptorRegistration registration = mock(InterceptorRegistration.class);
        when(registry.addInterceptor(eq(operationLogInterceptor))).thenReturn(registration);
        when(registration.addPathPatterns("/**")).thenReturn(registration);
        when(registration.excludePathPatterns(org.mockito.ArgumentMatchers.<String[]>any())).thenReturn(registration);

        new WebConfig(operationLogInterceptor, "http://localhost:*")
                .addInterceptors(registry);

        verify(registry).addInterceptor(operationLogInterceptor);
        verifyNoMoreInteractions(registry);
    }
}
