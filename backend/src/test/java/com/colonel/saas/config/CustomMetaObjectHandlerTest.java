package com.colonel.saas.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CustomMetaObjectHandlerTest {

    private final CustomMetaObjectHandler handler = new CustomMetaObjectHandler();

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void getCurrentUserId_shouldReadUuidFromRequestAttribute() {
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userId", userId);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(handler.getCurrentUserId()).isEqualTo(userId);
    }

    @Test
    void getCurrentUserId_shouldParseStringUuidFromRequestAttribute() {
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userId", userId.toString());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(handler.getCurrentUserId()).isEqualTo(userId);
    }

    @Test
    void getCurrentUserId_shouldReturnNullWhenAttributeInvalid() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userId", "not-a-uuid");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(handler.getCurrentUserId()).isNull();
    }

    @Test
    void getCurrentUserId_shouldReturnNullWithoutRequestContext() {
        assertThat(handler.getCurrentUserId()).isNull();
    }
}
