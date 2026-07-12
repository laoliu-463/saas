package com.colonel.saas.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * DDD refactor safety switches (DDD-BASE-001).
 *
 * <p>This bean only binds {@code ddd.refactor.*} configuration values. It does not
 * select an implementation path by itself, so adding the bean must not change any
 * current business behavior.</p>
 *
 * <p>The checked-in runtime profiles default every switch to {@code true}, making
 * the DDD path the primary path. An explicit environment variable set to
 * {@code false} remains the rollback escape hatch. A directly constructed bean
 * keeps Java's {@code false} defaults for isolated characterization tests.</p>
 *
 * <pre>
 * ddd:
 *   refactor:
 *     enabled: true
 *     user-facade:
 *       enabled: true
 *     config-facade:
 *       enabled: true
 *     product-facade:
 *       enabled: true
 *     product-display-policy:
 *       enabled: true
 *     talent-facade:
 *       enabled: true
 *     sample-application:
 *       enabled: true
 *     order-application:
 *       enabled: true
 *     order-attribution:
 *       enabled: true
 *     order-amount-policy:
 *       enabled: true
 *     performance-calc:
 *       enabled: true
 *     performance-query:
 *       enabled: true
 *     analytics-shadow:
 *       enabled: true
 *     outbox:
 *       enabled: true
 *     data-scope-policy:
 *       enabled: true
 *     sample-homework-event:
 *       enabled: true
 *     colonel-partner-contact:
 *       enabled: true
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "ddd.refactor")
public class DddRefactorProperties {

    /**
     * Root refactor switch. It is intentionally passive in DDD-BASE-001.
     */
    private boolean enabled = false;

    private Switch userFacade = new Switch();

    private Switch configFacade = new Switch();

    private Switch productFacade = new Switch();

    /** 商品展示 / 活动商品查询规则旁路；runtime profile 默认 ON。 */
    private Switch productDisplayPolicy = new Switch();

    private Switch talentFacade = new Switch();

    private Switch sampleApplication = new Switch();

    private Switch orderApplication = new Switch();

    private Switch orderAttribution = new Switch();

    private Switch orderAmountPolicy = new Switch();

    private Switch performanceCalc = new Switch();

    private Switch performanceQuery = new Switch();

    private Switch analyticsShadow = new Switch();

    private Switch outbox = new Switch();

    /**
     * 数据范围过滤（DataScopePolicy）灰度开关（DDD-DATASCOPE-001，P1 修复）。
     *
     * <p>控制业务域数据范围调用点是否走 DataScopePolicy 路径。
     * OFF = 旧实现，ON = 新 Policy 路径。</p>
     *
     * <p>runtime profile 默认 ON；显式设置为 false 时保留 Legacy 回滚路径。</p>
     */
    private Switch dataScopePolicy = new Switch();

    /** 寄样交作业改由 {@link OrderSyncedEvent} 驱动（DDD-SAMPLE-004）。 */
    private Switch sampleHomeworkEvent = new Switch();

    /** 团长合作伙伴联系方式更新走 DDD Application Service（DDD-COLONEL-001 测试 DDD 化）。 */
    private Switch colonelPartnerContact = new Switch();

    /** A nested switch bound from keys such as {@code ddd.refactor.user-facade.enabled}. */
    @Data
    public static class Switch {
        private boolean enabled = false;
    }
}
