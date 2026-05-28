package com.colonel.saas.service.talent;

import com.colonel.saas.common.enums.TalentDataSource;

/**
 * 达人数据供应者接口 —— 定义达人资料采集（Enrichment）的数据源契约。
 *
 * <p><b>职责：</b></p>
 * <ul>
 *   <li>声明数据源类型（{@link #source()}），供编排器识别来源</li>
 *   <li>提供优先级（{@link #priority()}），数值越小优先级越高，默认 100</li>
 *   <li>判断是否支持当前达人上下文（{@link #supports(TalentEnrichContext)}）</li>
 *   <li>执行数据采集并返回不可变的采集结果（{@link #enrich(TalentEnrichContext)}）</li>
 * </ul>
 *
 * <p><b>在架构中的角色：</b>采用策略模式（Strategy Pattern），
 * 由 {@link TalentEnrichOrchestrator} 按优先级顺序遍历所有实现，
 * 第一个返回有效数据的供应者胜出（First-Win 策略）。
 * 各实现类按数据源命名，如 DouyinApi、Crawler、Mock 等。</p>
 *
 * <p><b>业务域：</b>达人域 / 达人资料采集子域</p>
 *
 * @see TalentEnrichOrchestrator
 * @see TalentEnrichResult
 * @see TalentEnrichContext
 */
public interface TalentDataProvider {

    /**
     * 返回当前数据源的类型标识。
     *
     * @return 数据源类型枚举（如 DOUYIN_API、CRAWLER、MOCK 等）
     */
    TalentDataSource source();

    /**
     * 返回当前供应者的优先级，数值越小越优先。
     *
     * <p>默认值为 100。实现类可覆写此方法以调整优先级顺序，
     * 例如真实 API 优先级应高于爬虫，爬虫高于 Mock。</p>
     *
     * @return 优先级数值（整数），默认 100
     */
    default int priority() {
        return 100;
    }

    /**
     * 判断当前供应者是否支持给定的达人采集上下文。
     *
     * <p>典型判断条件包括：数据源是否配置、Token 是否有效、
     * 达人标识是否完整（如 secUid 是否存在）等。</p>
     *
     * @param context 达人采集上下文，包含达人实体和强制刷新标志
     * @return {@code true} 表示当前供应者可以处理该上下文，{@code false} 则跳过
     */
    boolean supports(TalentEnrichContext context);

    /**
     * 执行达人数据采集，返回采集结果。
     *
     * <p>前置条件：{@link #supports(TalentEnrichContext)} 已返回 {@code true}。</p>
     *
     * @param context 达人采集上下文，包含达人实体和强制刷新标志
     * @return 采集结果（{@link TalentEnrichResult}），可能为空结果（无字段）但不应为 {@code null}
     */
    TalentEnrichResult enrich(TalentEnrichContext context);
}
