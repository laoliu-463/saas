package com.colonel.saas.config;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

/**
 * 在 Spring 调度方法进入业务代码前执行只读 Schema 契约检查。
 *
 * <p>此保护只作用于显式标注的同步任务，不改变人工触发接口或其他定时任务语义。</p>
 */
@Slf4j
@Aspect
@Component
public class SchemaCompatibleScheduleAspect {

    private final SchemaCompatibilityProbe probe;
    private final boolean schedulingEnabled;

    public SchemaCompatibleScheduleAspect(
            SchemaCompatibilityProbe probe,
            @Value("${app.scheduling.enabled:true}") boolean schedulingEnabled) {
        this.probe = probe;
        this.schedulingEnabled = schedulingEnabled;
    }

    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    public Object guardScheduledSync(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!schedulingEnabled) {
            log.info("Scheduled task paused by app.scheduling.enabled=false, task={}",
                    joinPoint.getSignature().toShortString());
            return null;
        }
        Class<?> targetType = AopUtils.getTargetClass(joinPoint.getTarget());
        if (AnnotationUtils.findAnnotation(targetType, RequiresCompatibleSchema.class) == null) {
            return joinPoint.proceed();
        }
        SchemaCompatibilityProbe.SchemaCheck check = probe.check();
        if (!check.compatible()) {
            log.error("Scheduled sync blocked by incompatible schema, task={}, details={}",
                    joinPoint.getSignature().toShortString(), check.details());
            return null;
        }
        return joinPoint.proceed();
    }
}
