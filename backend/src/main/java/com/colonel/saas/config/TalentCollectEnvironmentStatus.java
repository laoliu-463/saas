package com.colonel.saas.config;

/**
 * 达人资料真实采集环境联调状态枚举。
 * <p>
 * 在 {@code real-pre} 环境下，用于记录达人资料采集链路的真实连通状态。
 * 该枚举遵循"文档口径"原则：禁止将未真实联调通过的状态虚报为 {@link #REAL_CONNECTED}。
 * 缺少 Token、缺少授权或上游接口不支持时，只能标记为对应的中间状态。
 * </p>
 *
 * <p>状态说明：</p>
 * <ul>
 *   <li>{@link #MOCK_ONLY} —— 仅使用 Mock 数据，未配置真实采集能力</li>
 *   <li>{@link #NOT_CONFIGURED} —— 未配置采集参数（如 API Key、爬虫地址等）</li>
 *   <li>{@link #NOT_AUTHORIZED} —— 已配置但授权无效（Token 过期、权限不足等）</li>
 *   <li>{@link #UNSUPPORTED} —— 上游抖音接口不支持该采集能力</li>
 *   <li>{@link #CRAWLER_FALLBACK} —— 主采集方式失败，回退到爬虫方式</li>
 *   <li>{@link #REAL_CONNECTED} —— 真实联调通过，完整闭环验证成功（严禁虚报）</li>
 * </ul>
 *
 * <p>与其他组件的关系：</p>
 * <ul>
 *   <li>{@link TalentCollectStartupReporter} —— 启动时读取此状态并输出到日志</li>
 *   <li>{@link TalentCollectProperties} —— 采集模式配置（mock/crawler/api）影响状态判断</li>
 * </ul>
 *
 * @see TalentCollectStartupReporter
 * @see TalentCollectProperties
 */
public enum TalentCollectEnvironmentStatus {

    /** 仅使用 Mock 数据，未接入真实采集 */
    MOCK_ONLY,
    /** 未配置采集参数（API Key、爬虫地址等） */
    NOT_CONFIGURED,
    /** 已配置但授权无效（Token 过期或权限不足） */
    NOT_AUTHORIZED,
    /** 上游抖音接口不支持该采集能力 */
    UNSUPPORTED,
    /** 主采集方式失败，已回退到爬虫方式 */
    CRAWLER_FALLBACK,
    /** 真实联调通过（严格禁止虚报此状态） */
    REAL_CONNECTED
}
