package com.colonel.saas.constant;

/**
 * 商品域领域事件类型常量类。
 * <p>
 * 定义商品域中所有领域事件的类型标识。这些事件通过 Spring 的 {@code ApplicationEventPublisher}
 * 发布，由对应的 Listener 监听处理，用于实现商品域内部以及跨域的事件驱动解耦。
 * </p>
 * <p>
 * 事件分类：
 * <ul>
 *   <li>商品状态事件：上架（Listed）、隐藏（Hidden）、强显变更（ForceDisplayChanged）</li>
 *   <li>商品归属事件：Owner 变更</li>
 *   <li>活动/合作同步事件：活动同步完成、合作同步完成、团长合作同步</li>
 *   <li>规则事件：展示规则应用完成</li>
 * </ul>
 * </p>
 *
 * @see com.colonel.saas.event.ConfigChangedApplicationEvent
 */
public final class ProductDomainEventTypes {

    /** 防止实例化 */
    private ProductDomainEventTypes() {
    }

    /** 商品上架事件 — 商品被标记为展示中时触发 */
    public static final String PRODUCT_LISTED = "ProductListedEvent";

    /** 商品隐藏事件 — 商品被标记为已隐藏时触发 */
    public static final String PRODUCT_HIDDEN = "ProductHiddenEvent";

    /** 商品 Owner 变更事件 — 商品的招商归属发生变更时触发 */
    public static final String PRODUCT_OWNER_CHANGED = "ProductOwnerChangedEvent";

    /** 活动同步完成事件 — 抖店活动数据同步到本地后触发 */
    public static final String ACTIVITY_SYNC_COMPLETED = "ActivitySyncCompletedEvent";

    /** 合作关系同步完成事件 — 合作伙伴数据同步完成后触发 */
    public static final String PARTNER_SYNC_COMPLETED = "PartnerSyncCompletedEvent";

    /** 活动延期事件 — 抖店活动截止日期延长时触发 */
    public static final String ACTIVITY_EXTENDED = "ActivityExtendedEvent";

    /** 商品展示规则应用事件 — 定时任务完成商品展示状态规则判定后触发 */
    public static final String PRODUCT_DISPLAY_RULE_APPLIED = "ProductDisplayRuleAppliedEvent";

    /** 商品强显状态变更事件 — 运营手动设置商品强制展示/隐藏后触发 */
    public static final String PRODUCT_FORCE_DISPLAY_CHANGED = "ProductForceDisplayChangedEvent";

    /** 商品转链完成事件 — 推广链接和 pick_source_mapping 均已落库后触发 */
    public static final String PRODUCT_PROMOTION_LINK_COMPLETED = "ProductPromotionLinkCompletedEvent";

    /** 团长合作同步事件 — 团长级别的合作关系同步完成后触发 */
    public static final String COLONEL_PARTNER_SYNCED = "ColonelPartnerSyncedEvent";
}
