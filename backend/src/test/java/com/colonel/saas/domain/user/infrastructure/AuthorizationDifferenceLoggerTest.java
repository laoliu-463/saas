package com.colonel.saas.domain.user.infrastructure;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.colonel.saas.domain.user.api.AuthorizationDecision;
import com.colonel.saas.domain.user.api.AuthorizationReason;
import com.colonel.saas.domain.user.api.AuthorizationRuntimeMode;
import com.colonel.saas.domain.user.api.PermissionCode;
import com.colonel.saas.domain.user.domain.AuthorizationComparison;
import com.colonel.saas.domain.user.domain.AuthorizationRuntimeDecision;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationDifferenceLoggerTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000062");

    private final Logger logger = (Logger) LoggerFactory.getLogger(AuthorizationDifferenceLogger.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Level originalLevel;

    @BeforeEach
    void captureLogs() {
        originalLevel = logger.getLevel();
        appender.start();
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);
        MDC.put("traceId", "trace-6b");
    }

    @AfterEach
    void restoreLogger() {
        MDC.clear();
        logger.detachAppender(appender);
        logger.setLevel(originalLevel);
        appender.stop();
    }

    @Test
    void log_emitsExactlyApprovedFieldsForEvaluatedDecisionWithoutStack() {
        AuthorizationRuntimeDecision decision = new AuthorizationRuntimeDecision(
                USER_ID,
                "sample",
                "sample:approve",
                AuthorizationRuntimeMode.SHADOW,
                true,
                AuthorizationDecision.deny(
                        new PermissionCode("sample:approve"),
                        "sample",
                        AuthorizationReason.PERMISSION_NOT_GRANTED),
                true,
                AuthorizationComparison.OLD_ALLOW_NEW_DENY);

        new AuthorizationDifferenceLogger().log(decision);

        assertSingleSafeEvent(
                "AUTHZ_SHADOW comparison=OLD_ALLOW_NEW_DENY mode=SHADOW "
                        + "userId=" + USER_ID + " domain=sample permission=sample:approve "
                        + "newReason=PERMISSION_NOT_GRANTED newScope=DENY traceId=trace-6b");
    }

    @Test
    void log_unavailableUsesStableReasonAndScopeWithoutExceptionDetails() {
        AuthorizationRuntimeDecision decision = new AuthorizationRuntimeDecision(
                USER_ID,
                "sample",
                "sample:approve",
                AuthorizationRuntimeMode.SHADOW,
                false,
                null,
                false,
                AuthorizationComparison.NEW_UNAVAILABLE);

        new AuthorizationDifferenceLogger().log(decision);

        assertSingleSafeEvent(
                "AUTHZ_SHADOW comparison=NEW_UNAVAILABLE mode=SHADOW "
                        + "userId=" + USER_ID + " domain=sample permission=sample:approve "
                        + "newReason=UNAVAILABLE newScope=DENY traceId=trace-6b");
    }

    @Test
    void log_replacesCrlfAndSensitiveTraceIdWithFixedPlaceholder() {
        String unsafeTraceId = "trace-safe\r\npassword=forbidden token=forbidden";
        MDC.put("traceId", unsafeTraceId);

        new AuthorizationDifferenceLogger().log(unavailableDecision());

        assertSingleSafeEvent(
                "AUTHZ_SHADOW comparison=NEW_UNAVAILABLE mode=SHADOW "
                        + "userId=" + USER_ID + " domain=sample permission=sample:approve "
                        + "newReason=UNAVAILABLE newScope=DENY traceId=INVALID");
        assertThat(appender.list.get(0).getFormattedMessage())
                .doesNotContain(unsafeTraceId, "password=forbidden", "token=forbidden");
    }

    @Test
    void log_replacesOverlongTraceIdWithoutWritingOriginalValue() {
        String overlongTraceId = "x".repeat(129);
        MDC.put("traceId", overlongTraceId);

        new AuthorizationDifferenceLogger().log(unavailableDecision());

        assertSingleSafeEvent(
                "AUTHZ_SHADOW comparison=NEW_UNAVAILABLE mode=SHADOW "
                        + "userId=" + USER_ID + " domain=sample permission=sample:approve "
                        + "newReason=UNAVAILABLE newScope=DENY traceId=INVALID");
        assertThat(appender.list.get(0).getFormattedMessage()).doesNotContain(overlongTraceId);
    }

    private static AuthorizationRuntimeDecision unavailableDecision() {
        return new AuthorizationRuntimeDecision(
                USER_ID,
                "sample",
                "sample:approve",
                AuthorizationRuntimeMode.SHADOW,
                false,
                null,
                false,
                AuthorizationComparison.NEW_UNAVAILABLE);
    }

    private void assertSingleSafeEvent(String expectedMessage) {
        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage())
                    .isEqualTo(expectedMessage)
                    .doesNotContain("\r", "\n")
                    .doesNotContain(
                            "token=",
                            "roles=",
                            "roleCodes=",
                            "permissions=",
                            "password=",
                            "body=",
                            "headers=",
                            "snapshot=",
                            "redis=",
                            "exceptionMessage=");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }
}
