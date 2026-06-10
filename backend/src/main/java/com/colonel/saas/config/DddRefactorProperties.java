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

    /** A nested switch bound from keys such as {@code ddd.refactor.user-facade.enabled}. */
    @Data
    public static class Switch {
        private boolean enabled = false;
    }
}
