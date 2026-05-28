package com.colonel.saas.service.talent;

import com.colonel.saas.config.TalentCollectEnvironmentStatus;
import com.colonel.saas.config.TalentCollectProperties;
import com.colonel.saas.douyin.DouyinTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 达人采集环境状态报告器 —— 诊断当前运行环境下达人资料采集链路的可用性。
 *
 * <p><b>职责：</b></p>
 * <ul>
 *   <li>检测当前环境是否处于 Mock-Only 模式（测试 profile 或显式配置）</li>
 *   <li>检查达人采集 API 是否启用且 Token 有效</li>
 *   <li>检查爬虫采集是否配置为降级方案</li>
 *   <li>返回结构化的环境状态枚举，供前端展示或运维诊断使用</li>
 * </ul>
 *
 * <p><b>在架构中的角色：</b>作为环境健康检查组件，为达人采集模块提供
 * "当前环境能做什么"的诊断信息。状态值包括：
 * MOCK_ONLY（仅模拟）、CRAWLER_FALLBACK（爬虫降级）、NOT_CONFIGURED（未配置）、
 * NOT_AUTHORIZED（Token 过期需重新授权）、UNSUPPORTED（平台接口不支持）。</p>
 *
 * <p><b>业务域：</b>达人域 / 达人资料采集子域 / 环境配置</p>
 *
 * @see TalentCollectEnvironmentStatus
 * @see TalentCollectProperties
 */
@Component
@RequiredArgsConstructor
public class TalentCollectEnvironmentReporter {

    /** 达人采集配置属性（是否 Mock-Only、API/爬虫开关等） */
    private final TalentCollectProperties collectProperties;

    /** 抖音 Token 服务，用于检查 Token 状态（是否过期、是否需要重新授权） */
    private final DouyinTokenService douyinTokenService;

    /** Spring 运行环境，用于读取 Active Profile 和自定义配置项 */
    private final Environment environment;

    /**
     * 解析当前环境的达人采集状态。
     *
     * <p>判断流程按优先级从高到低：</p>
     * <ol>
     *   <li>若处于测试采集 profile（test + talent.enrich.mode=test），返回 MOCK_ONLY</li>
     *   <li>若配置为 Mock-Only 模式（collectProperties.isMockOnly()），返回 MOCK_ONLY</li>
     *   <li>若 API 未启用或未允许，检查爬虫是否可用：可用则 CRAWLER_FALLBACK，否则 NOT_CONFIGURED</li>
     *   <li>若无 AccessToken 且无 RefreshToken，返回 NOT_CONFIGURED</li>
     *   <li>若 Token 已过期需重新授权，返回 NOT_AUTHORIZED</li>
     *   <li>以上均通过但平台接口不支持达人资料拉取，返回 UNSUPPORTED</li>
     * </ol>
     *
     * @return 当前环境的达人采集状态枚举
     */
    public TalentCollectEnvironmentStatus resolveStatus() {
        // 第一步：检查是否处于测试采集 profile（test 且 enrich.mode=test）
        if (isTestEnrichProfile()) {
            return TalentCollectEnvironmentStatus.MOCK_ONLY;
        }
        // 第二步：检查配置是否显式设置为 Mock-Only 模式
        if (collectProperties.isMockOnly()) {
            return TalentCollectEnvironmentStatus.MOCK_ONLY;
        }
        // 第三步：检查 API 是否启用且允许调用
        if (!collectProperties.isApiAllowed() || !collectProperties.getApi().isEnabled()) {
            // API 不可用时，检查爬虫是否配置为降级方案
            return collectProperties.isCrawlerAllowed()
                    ? TalentCollectEnvironmentStatus.CRAWLER_FALLBACK
                    : TalentCollectEnvironmentStatus.NOT_CONFIGURED;
        }
        // 第四步：检查抖音 Token 状态
        DouyinTokenService.TokenStatus tokenStatus = douyinTokenService.getTokenStatus(null);
        if (!tokenStatus.isHasAccessToken() && !tokenStatus.isHasRefreshToken()) {
            return TalentCollectEnvironmentStatus.NOT_CONFIGURED;
        }
        if (tokenStatus.isReauthorizeRequired()) {
            return TalentCollectEnvironmentStatus.NOT_AUTHORIZED;
        }
        // 第五步：所有前置条件通过，但抖店开放接口当前无达人主页资料拉取能力（TalentApi 仅转链等），不得宣称 REAL_CONNECTED
        return TalentCollectEnvironmentStatus.UNSUPPORTED;
    }

    /**
     * 返回当前环境状态的标签名（用于前端展示或 API 响应）。
     *
     * @return 环境状态的 {@code name()} 字符串，如 "MOCK_ONLY"、"NOT_CONFIGURED" 等
     */
    public String resolveStatusLabel() {
        return resolveStatus().name();
    }

    /**
     * 判断当前是否处于测试采集 profile 环境。
     *
     * <p>满足以下任一条件返回 {@code true}：</p>
     * <ul>
     *   <li>Active Profile 包含 "test"，且 {@code talent.enrich.mode} 配置为 "test"</li>
     *   <li>全局 {@code talent.enrich.mode} 配置为 "test"（不论 profile）</li>
     * </ul>
     *
     * @return {@code true} 表示处于测试采集环境
     */
    private boolean isTestEnrichProfile() {
        // 第一步：检查 Active Profile 是否为 test，并结合 enrich.mode 判断
        for (String profile : environment.getActiveProfiles()) {
            if ("test".equalsIgnoreCase(profile)) {
                String enrichMode = environment.getProperty("talent.enrich.mode", "");
                if ("test".equalsIgnoreCase(enrichMode)) {
                    return true;
                }
            }
        }
        // 第二步：全局检查 talent.enrich.mode 配置项
        String enrichMode = environment.getProperty("talent.enrich.mode", "");
        return StringUtils.hasText(enrichMode) && "test".equalsIgnoreCase(enrichMode.trim());
    }
}
