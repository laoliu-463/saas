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
 * <p>All switches default to {@code false}. Future DDD tasks may read these flags
 * after their own protection tests are in place.</p>
 *
 * <pre>
 * ddd:
 *   refactor:
 *     enabled: false
 *     user-facade:
 *       enabled: false
 *     config-facade:
 *       enabled: false
 *     product-facade:
 *       enabled: false
 *     talent-facade:
 *       enabled: false
 *     sample-application:
 *       enabled: false
 *     order-application:
 *       enabled: false
 *     order-attribution:
 *       enabled: false
 *     order-amount-policy:
 *       enabled: false
 *     performance-calc:
 *       enabled: false
 *     performance-query:
 *       enabled: false
 *     analytics-shadow:
 *       enabled: false
 *     outbox:
 *       enabled: false
 *     data-scope-policy:
 *       enabled: false
 *     sample-homework-event:
 *       enabled: false
 *     colonel-partner-contact:
 *       enabled: false
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
     * <p>默认 OFF（生产零变化）。</p>
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
