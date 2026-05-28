package com.colonel.saas.config;

/**
 * 订单域衍生数据的 Redis 缓存键常量。
 * <p>
 * 集中管理订单域中由原始订单数据衍生出的聚合查询（如仪表盘统计、筛选选项等）的
 * 缓存键前缀，避免各处散落硬编码字符串导致的维护困难和拼写错误。
 * </p>
 *
 * <p>缓存键设计原则：</p>
 * <ul>
 *   <li>使用冒号分隔的层级结构，便于 Redis 按前缀批量管理</li>
 *   <li>前缀末尾带冒号，调用方只需追加业务标识即可构成完整键名</li>
 *   <li>仅定义前缀常量，具体键名由业务层在运行时拼接用户/租户标识</li>
 * </ul>
 *
 * <p>与其他组件的关系：</p>
 * <ul>
 *   <li>配合 {@link ShortTtlCacheRedisInvalidationConfig} 的 Redis Pub/Sub 缓存失效机制</li>
 *   <li>配合 {@link ShortTtlCacheService} 进行本地 + Redis 双层缓存管理</li>
 * </ul>
 *
 * @see ShortTtlCacheRedisInvalidationConfig
 */
public final class OrderDerivedCacheKeys {

    /** 仪表盘汇总数据缓存前缀（如订单数、金额统计等） */
    public static final String DASHBOARD_SUMMARY_PREFIX = "dashboard:summary:";
    /** 仪表盘指标数据缓存前缀（如图表数据、趋势分析等） */
    public static final String DASHBOARD_METRICS_PREFIX = "dashboard:metrics:";
    /** 订单筛选选项缓存前缀（如下拉列表中的达人、商家、状态选项等） */
    public static final String FILTER_OPTIONS_PREFIX = "orders:filter-options:";

    /** 工具类，禁止实例化 */
    private OrderDerivedCacheKeys() {
    }
}
