package com.colonel.saas.service.talent.profile;

/**
 * 达人资料同步提供者接口 —— 定义达人资料同步（Profile Sync）的策略链契约。
 *
 * <p><b>职责：</b></p>
 * <ul>
 *   <li>声明提供者唯一标识（{@link #providerCode()}），用于日志追踪和同步记录</li>
 *   <li>提供执行优先级（{@link #order()}），数值越小越优先，由
 *       {@link com.colonel.saas.service.talent.profile.TalentProfileSyncService} 按升序遍历</li>
 *   <li>判断是否支持当前查询（{@link #supports(TalentProfileQuery)}），
 *       按输入类型、配置开关、Token 状态等条件筛选</li>
 *   <li>执行资料采集（{@link #fetch(TalentProfileQuery)}），
 *       返回包含字段数据或错误信息的采集结果</li>
 * </ul>
 *
 * <p><b>在架构中的角色：</b>采用策略模式（Strategy Pattern），由
 * {@link TalentProfileSyncService} 按 {@link #order()} 升序遍历所有实现，
 * 第一个返回成功且包含真实数据的提供者胜出（First-Win 策略）。
 * 当前实现链：</p>
 * <ol>
 *   <li>DouyinApiTalentProfileProvider（order=5）—— 抖店官方 API 通道（当前仅做 Token 校验）</li>
 *   <li>ConfigurableHttpTalentProvider（order=10）—— 可配置外部 HTTP 端点通道</li>
 *   <li>PublicWebTalentProvider（order=20）—— 公开页面爬取通道</li>
 *   <li>ManualTalentProvider（order=90）—— 手动填写通道（仅手动模式触发）</li>
 * </ol>
 *
 * <p><b>业务域：</b>达人域 / 达人资料采集子域 / 资料同步通道</p>
 *
 * @see TalentProfileSyncService
 * @see TalentProfileQuery
 * @see TalentProfileResult
 */
public interface TalentProfileProvider {

    /**
     * 返回提供者的唯一标识码，用于日志追踪和同步日志记录。
     *
     * @return 提供者标识字符串（如 "API"、"CRAWLER"、"configurable_http"、"manual"）
     */
    String providerCode();

    /**
     * 返回提供者的执行优先级，数值越小越优先被调用。
     *
     * @return 优先级整数值（如 5、10、20、90）
     */
    int order();

    /**
     * 判断当前提供者是否支持给定的查询请求。
     *
     * <p>典型判断条件包括：提供者是否启用、采集配置是否允许、
     * Token 是否有效、查询输入是否非空、是否为手动填写模式等。</p>
     *
     * @param query 达人资料查询请求
     * @return {@code true} 表示当前提供者可以处理该查询，{@code false} 则跳过
     */
    boolean supports(TalentProfileQuery query);

    /**
     * 执行达人资料采集，返回采集结果。
     *
     * <p>前置条件：{@link #supports(TalentProfileQuery)} 已返回 {@code true}。</p>
     *
     * @param query 达人资料查询请求
     * @return 采集结果（{@link TalentProfileResult}），成功时包含达人资料字段，
     *         失败时包含错误码和错误信息，不应返回 {@code null}
     */
    TalentProfileResult fetch(TalentProfileQuery query);
}
