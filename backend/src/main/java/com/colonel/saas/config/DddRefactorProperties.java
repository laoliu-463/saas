package com.colonel.saas.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * DDD 渐进式重构安全开关（DDD-BASE-001）。
 * <p>
 * 本类用于将 {@code ddd.refactor.*} 配置项绑定为类型安全的 Spring Bean。
 * 所有开关在默认配置下全部为 {@code false}，意味着线上行为不会发生任何变化。
 * </p>
 *
 * <p>使用原则：</p>
 * <ul>
 *   <li>开关未开启时，新旧实现路径保持完全一致；不允许出现"开启后行为偏差"</li>
 *   <li>每个子开关对应一个领域重构点：仅当该领域任务通过 ADR 评审后才允许打开</li>
 *   <li>任意一个子开关关闭时，对应领域的实现路径必须回退到重构前的旧实现</li>
 *   <li>本类不持有任何业务字段，只承载开关，禁止往里塞运行时数据</li>
 * </ul>
 *
 * <p>配置示例（application.yml）：</p>
 * <pre>
 * ddd:
 *   refactor:
 *     enabled: false
 *     user-scope:
 *       enabled: false
 *     order-attribution:
 *       enabled: false
 *     performance-calc:
 *       enabled: false
 *     product-display:
 *       enabled: false
 *     sample-policy:
 *       enabled: false
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "ddd.refactor")
public class DddRefactorProperties {

    /**
     * 重构总开关。关闭时所有子开关即使打开也不生效。
     */
    private boolean enabled = false;

    /** 用户域 self / group / all 数据范围重构开关 */
    private UserScope userScope = new UserScope();

    /** 订单 / 业绩归属与冲正重构开关 */
    private OrderAttribution orderAttribution = new OrderAttribution();

    /** 业绩域提成与双轨金额计算重构开关 */
    private PerformanceCalc performanceCalc = new PerformanceCalc();

    /** 商品域转链与展示层重构开关 */
    private ProductDisplay productDisplay = new ProductDisplay();

    /** 寄样域策略与状态机重构开关 */
    private SamplePolicy samplePolicy = new SamplePolicy();

    /** 用户域 self / group / all 数据范围子开关。 */
    @Data
    public static class UserScope {
        private boolean enabled = false;
    }

    /** 订单 / 业绩归属子开关。 */
    @Data
    public static class OrderAttribution {
        private boolean enabled = false;
    }

    /** 业绩域提成与双轨金额计算子开关。 */
    @Data
    public static class PerformanceCalc {
        private boolean enabled = false;
    }

    /** 商品域转链与展示层子开关。 */
    @Data
    public static class ProductDisplay {
        private boolean enabled = false;
    }

    /** 寄样域策略与状态机子开关。 */
    @Data
    public static class SamplePolicy {
        private boolean enabled = false;
    }
}
