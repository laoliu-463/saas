package com.colonel.saas.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SchemaCompatibleScheduleAspectTest {

    @Test
    void incompatibleSchemaShouldBlockScheduledBusinessCode() throws Throwable {
        SchemaCompatibilityProbe probe = mock(SchemaCompatibilityProbe.class);
        ProceedingJoinPoint joinPoint = joinPoint("OrderSyncJob.syncOrders()", new SchemaDependentJob());
        when(probe.check()).thenReturn(new SchemaCompatibilityProbe.SchemaCheck(
                false, List.of(), List.of("colonelsettlement_order.channel_attribution_source"), null));

        Object result = new SchemaCompatibleScheduleAspect(probe, true).guardScheduledSync(joinPoint);

        assertThat(result).isNull();
        verify(joinPoint, never()).proceed();
    }

    @Test
    void compatibleSchemaShouldAllowScheduledBusinessCode() throws Throwable {
        SchemaCompatibilityProbe probe = mock(SchemaCompatibilityProbe.class);
        ProceedingJoinPoint joinPoint = joinPoint("ProductActivitySyncJob.syncAll()", new SchemaDependentJob());
        when(probe.check()).thenReturn(new SchemaCompatibilityProbe.SchemaCheck(
                true, List.of(), List.of(), null));
        when(joinPoint.proceed()).thenReturn("executed");

        Object result = new SchemaCompatibleScheduleAspect(probe, true).guardScheduledSync(joinPoint);

        assertThat(result).isEqualTo("executed");
        verify(joinPoint).proceed();
    }

    @Test
    void globalPauseShouldBlockEveryScheduledTaskWithoutQueryingSchema() throws Throwable {
        SchemaCompatibilityProbe probe = mock(SchemaCompatibilityProbe.class);
        ProceedingJoinPoint joinPoint = joinPoint("LogCleanupJob.cleanup()", new Object());

        Object result = new SchemaCompatibleScheduleAspect(probe, false).guardScheduledSync(joinPoint);

        assertThat(result).isNull();
        verify(joinPoint, never()).proceed();
        verify(probe, never()).check();
    }

    private static ProceedingJoinPoint joinPoint(String shortSignature, Object target) {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(signature.toShortString()).thenReturn(shortSignature);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getTarget()).thenReturn(target);
        return joinPoint;
    }

    @RequiresCompatibleSchema
    private static final class SchemaDependentJob {
    }
}
