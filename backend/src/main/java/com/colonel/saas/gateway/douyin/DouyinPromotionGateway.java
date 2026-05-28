package com.colonel.saas.gateway.douyin;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 抖音推广链接 Gateway 接口。
 * <p>
 * 封装抖店推广链接生成和上游直连探测能力。业务层（转链服务）依赖此接口
 * 为达人生成专属推广链接（含 pick_source 和短链接）。
 * </p>
 *
 * <h3>实现切换</h3>
 * <p>
 * 通过配置 {@code douyin.test.enabled} 控制注入的实现：
 * <ul>
 *   <li>{@code true} - {@link com.colonel.saas.gateway.douyin.test.TestDouyinPromotionGateway}，返回 Mock 链接</li>
 *   <li>{@code false} - {@link com.colonel.saas.gateway.douyin.real.RealDouyinPromotionGateway}，调用抖店 SDK</li>
 * </ul>
 * </p>
 *
 * <h3>安全策略</h3>
 * <p>
 * Real 实现在生成推广链接前会进行安全门控校验（safety gate），确保请求参数合法。
 * </p>
 */
public interface DouyinPromotionGateway {

    /**
     * 生成推广链接。
     * <p>
     * 根据商品 ID 列表和推广上下文，调用抖店 SDK 生成推广链接。
     * 返回结果包含 pick_source（用于订单追踪归属）、短链接和推广链接。
     * </p>
     *
     * @param command 推广链接生成命令（含商品列表、推广场景、上下文信息）
     * @return 推广链接结果（含 pick_source、短链接、推广链接等）
     */
    PromotionLinkResult generateLink(PromotionLinkCommand command);

    /**
     * 低层上游直连 POST（管理后台探针专用）。
     * <p>
     * 允许以任意方法名和 JSON 请求体直接调用抖店上游 API，
     * 用于管理后台的联调探测和问题排查。
     * </p>
     *
     * @param appId   应用 ID
     * @param method  抖店 API 方法名（如 "buyin.promotionLinkCreate"）
     * @param payload 请求参数 Map
     * @return 上游原始响应 Map
     */
    Map<String, Object> rawUpstreamPost(String appId, String method, Map<String, Object> payload);

    /**
     * 推广链接生成命令。
     * <p>
     * 封装生成推广链接所需的全部参数。
     * </p>
     *
     * @param externalUniqueId 外部唯一请求 ID（用于幂等和链路追踪）
     * @param promotionScene   推广场景（抖店侧枚举值）
     * @param productIds       需要推广的商品 ID 列表
     * @param needShortLink    是否需要短链接
     * @param context          推广上下文（含用户、部门、达人等归属信息）
     */
    record PromotionLinkCommand(
            String externalUniqueId,
            int promotionScene,
            List<String> productIds,
            boolean needShortLink,
            PromotionContext context) {
    }

    /**
     * 推广上下文信息。
     * <p>
     * 记录推广操作的发起者（用户/达人）和关联业务对象，用于订单归属追踪。
     * </p>
     *
     * @param userId      发起推广的用户 ID
     * @param deptId      用户所属部门 ID
     * @param productId   关联商品 ID
     * @param activityId  关联活动 ID
     * @param sourceUrl   来源 URL
     * @param scene       推广场景标识
     * @param talentId    达人 ID
     * @param pickExtra   附加追踪信息（JSON 格式，由业务层自定义）
     */
    record PromotionContext(
            UUID userId,
            UUID deptId,
            String productId,
            String activityId,
            String sourceUrl,
            String scene,
            String talentId,
            String pickExtra) {
    }

    /**
     * 推广链接生成结果。
     * <p>
     * 包含抖店返回的推广链接信息，pick_source 用于订单结算时的归属追踪。
     * </p>
     *
     * @param pickSource  推广来源标识（订单结算归属的关键字段）
     * @param pickExtra   附加追踪信息
     * @param shortId     短链接 ID
     * @param shortLink   短链接 URL
     * @param promoteLink 原始推广链接 URL
     * @param uuidSeed    UUID 种子（用于下游关联）
     */
    record PromotionLinkResult(
            String pickSource,
            String pickExtra,
            String shortId,
            String shortLink,
            String promoteLink,
            String uuidSeed) {
    }
}
