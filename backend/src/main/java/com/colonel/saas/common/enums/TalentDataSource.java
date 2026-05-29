package com.colonel.saas.common.enums;

/**
 * 达人信息来源枚举，定义达人数据的采集渠道及合规优先级。
 *
 * <p>不同来源的达人信息在可信度和合规性上存在差异，
 * 系统在冲突合并时按照优先级从高到低选择数据源：
 * {@code OFFICIAL_API > MANUAL > INTERNAL_BUSINESS > THIRD_PARTY > PUBLIC_PAGE}</p>
 *
 * <h3>枚举值说明</h3>
 * <ul>
 *   <li>{@link #TEST} — 测试专用来源，仅用于单元测试和联调环境，不进入生产数据</li>
 *   <li>{@link #OFFICIAL_API} — 通过抖音开放平台官方 API 获取，可信度最高</li>
 *   <li>{@link #MANUAL} — 由运营人员手动录入，可信度次之</li>
 *   <li>{@link #INTERNAL_BUSINESS} — 内部业务渠道获取（如招商组长线下对接）</li>
 *   <li>{@link #THIRD_PARTY} — 第三方数据服务商提供的达人信息</li>
 *   <li>{@link #PUBLIC_PAGE} — 从抖音公开页面抓取，可信度最低，可能存在延迟或不准确</li>
 * </ul>
 *
 * <h3>业务关联</h3>
 * <p>该枚举存储在达人实体的 {@code data_source} 字段中，
 * 在达人信息合并、冲突检测和审计追踪时作为关键判断依据。</p>
 */
public enum TalentDataSource {
    /** 测试专用来源，仅用于单元测试和联调环境，生产环境不应出现 */
    TEST,
    /** 抖音开放平台官方 API，可信度最高 */
    OFFICIAL_API,
    /** 运营人员手动录入 */
    MANUAL,
    /** 内部业务渠道获取（如招商组长线下对接） */
    INTERNAL_BUSINESS,
    /** 第三方数据服务商提供 */
    THIRD_PARTY,
    /** 抖音公开页面抓取，可信度最低 */
    PUBLIC_PAGE
}
