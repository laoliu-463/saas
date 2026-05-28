package com.colonel.saas.gateway.douyin.contract;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 抖音上游数据源模式切换支持组件。
 *
 * <p>功能描述：根据配置项 {@code douyin.real.upstream-mode} 决定抖音 Gateway 的数据来源模式。
 * 支持两种模式：
 * <ul>
 *   <li>{@code live}（默认）：调用抖音真实 API 获取数据，用于 production / real-pre 环境</li>
 *   <li>{@code contract}：使用 {@link DouyinContractFixtureProvider} 提供的契约测试夹具数据，
 *       用于 contract test 场景，不依赖真实抖音 API 可用性</li>
 * </ul>
 * </p>
 *
 * <p>环境说明：
 * <ul>
 *   <li>real / real-pre 环境：通常配置为 {@code live}，调用真实上游</li>
 *   <li>contract test 环境：配置为 {@code contract}，返回硬编码的夹具数据</li>
 *   <li>默认值为 {@code live}，未配置时自动回退到真实模式</li>
 * </ul>
 * </p>
 *
 * <p>所属业务领域：抖音网关 / 联调与契约测试基础设施</p>
 *
 * @see DouyinContractFixtureProvider
 */
@Component
public class DouyinUpstreamModeSupport {

    /** 当前上游模式值，标准化后只可能是 "live" 或 "contract" */
    private final String mode;

    /**
     * 构造函数，从配置中读取并标准化上游模式。
     *
     * @param rawMode 配置项 {@code douyin.real.upstream-mode} 的原始值，默认为 "live"
     */
    public DouyinUpstreamModeSupport(@Value("${douyin.real.upstream-mode:live}") String rawMode) {
        this.mode = normalize(rawMode);
    }

    /**
     * 判断当前是否为契约测试模式。
     *
     * @return {@code true} 表示使用契约夹具数据，{@code false} 表示使用真实上游
     */
    public boolean isContract() {
        return "contract".equals(mode);
    }

    /**
     * 判断当前是否为真实上游模式。
     *
     * @return {@code true} 表示调用真实抖音 API，{@code false} 表示使用夹具数据
     */
    public boolean isLive() {
        return "live".equals(mode);
    }

    /**
     * 返回当前上游模式的原始值。
     *
     * @return "live" 或 "contract"
     */
    public String value() {
        return mode;
    }

    /**
     * 标准化配置值：去除空白、转小写，只接受 "contract"，其余一律视为 "live"。
     *
     * @param rawMode 原始配置值
     * @return 标准化后的模式字符串
     */
    private String normalize(String rawMode) {
        if (rawMode == null) {
            return "live";
        }
        String normalized = rawMode.trim().toLowerCase(Locale.ROOT);
        if ("contract".equals(normalized)) {
            return "contract";
        }
        return "live";
    }
}
