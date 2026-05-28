package com.colonel.saas.gateway.douyin.test;

import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 测试环境抖店推广转链网关适配器。
 * <p>
 * 实现 {@link DouyinPromotionGateway} 接口，在 {@code douyin.test.enabled=true} 时替代真实的
 * 抖店推广转链网关，为本地开发和 test 环境提供不依赖真实抖店开放平台的 Mock 转链数据。
 * </p>
 *
 * <ul>
 *   <li><b>生成推广链接（generateLink）</b>：根据活动 ID、商品 ID 和随机 UUID 生成 Mock 的 pick_source、pick_extra、短链和推广链接</li>
 *   <li><b>透传上游 POST 请求（rawUpstreamPost）</b>：将请求参数原样返回，用于调试和日志追踪</li>
 * </ul>
 *
 * <p>架构角色：Gateway 测试适配器（Test Double），所属领域：商品域（转链）。
 * 与真实网关的关系：实现同一 {@link DouyinPromotionGateway} 接口，通过 {@code douyin.test.enabled}
 * 属性切换。Mock 转链结果中的 pick_source 使用 {@code MOCK + 哈希后缀} 格式，
 * 便于在日志和数据库中快速区分 Mock 数据与真实数据。</p>
 *
 * @see DouyinPromotionGateway
 */
@Component
@ConditionalOnProperty(name = "douyin.test.enabled", havingValue = "true")
public class TestDouyinPromotionGateway implements DouyinPromotionGateway {

    @Override
    public PromotionLinkResult generateLink(PromotionLinkCommand command) {
        String activityId = command.context() == null ? "unknown-activity" : command.context().activityId();
        String productId = command.productIds() == null || command.productIds().isEmpty()
                ? "unknown-product"
                : command.productIds().get(0);
        UUID uuidSeed = UUID.randomUUID();
        String suffix = String.valueOf(Math.abs((activityId + "-" + productId + "-" + uuidSeed).hashCode()));
        String shortId = "MOCK" + suffix.substring(0, Math.min(6, suffix.length()));
        String pickSource = shortId;
        String pickExtra = command.context() != null && org.springframework.util.StringUtils.hasText(command.context().pickExtra())
                ? command.context().pickExtra()
                : shortId;
        String shortLink = "https://test.short.link/" + shortId;
        String promoteLink = "https://test.promote.link/activity/" + activityId + "/product/" + productId + "?pick_source=" + pickSource;

        return new PromotionLinkResult(
                pickSource,
                pickExtra,
                shortId,
                shortLink,
                promoteLink,
                uuidSeed.toString()
        );
    }

    @Override
    public Map<String, Object> rawUpstreamPost(String appId, String method, Map<String, Object> payload) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (payload != null) {
            data.putAll(payload);
        }
        data.put("method", method == null ? "" : method);
        if (appId != null && !appId.isBlank()) {
            data.put("appId", appId.trim());
        }
        return Map.of("code", 10000, "msg", "success", "data", data);
    }
}

