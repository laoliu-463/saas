package com.colonel.saas.service.talent;

import com.colonel.saas.entity.Talent;

/**
 * 达人采集上下文 —— 封装单次达人资料采集（Enrichment）所需的全部输入。
 *
 * <p><b>职责：</b></p>
 * <ul>
 *   <li>持有当前待采集的达人实体（{@link #talent}）</li>
 *   <li>携带强制刷新标志（{@link #forceRefresh}），决定是否忽略缓存直接拉取最新数据</li>
 * </ul>
 *
 * <p><b>在架构中的角色：</b>由 {@link TalentEnrichOrchestrator} 构造并传递给
 * 各 {@link TalentDataProvider} 实现。供应者通过 {@code context.talent()} 获取达人标识
 * （如 secUid、douyinUid）来定位达人资料，通过 {@code context.forceRefresh()} 决定缓存策略。</p>
 *
 * <p><b>业务域：</b>达人域 / 达人资料采集子域</p>
 *
 * @param talent       待采集的达人实体，包含 secUid、douyinUid 等标识信息
 * @param forceRefresh 是否强制刷新：{@code true} 表示跳过缓存直接请求最新数据，
 *                     {@code false} 表示优先使用已有缓存
 * @see TalentDataProvider
 * @see TalentEnrichOrchestrator
 */
public record TalentEnrichContext(Talent talent, boolean forceRefresh) {
}

