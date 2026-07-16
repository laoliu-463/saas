package com.colonel.saas.domain.product.application;

import com.colonel.saas.domain.config.facade.ConfigDomainFacade;
import com.colonel.saas.domain.product.application.dto.PromotionLinkCopyResult;
import com.colonel.saas.domain.product.application.port.CopyPromotionSupportPort;
import com.colonel.saas.domain.product.policy.CopyTextPolicy;
import com.colonel.saas.domain.shared.attribution.AttributionOwnerType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 复制推广简介应用层（DDD-PRODUCT-004）。
 *
 * <p>把 {@code ProductService.generatePromotionLinkCopy} 的业务编排抽到本服务：
 * 状态前置校验 → 转链 Port 调用 → 复制文本渲染 → 返回结果。</p>
 *
 * <p>纯文本渲染由 {@link CopyTextPolicy} 完成；商品上下文读取和转链执行通过
 * {@link CopyPromotionSupportPort} 过渡，避免应用层直接依赖 legacy 大 Service。</p>
 */
@Service
public class CopyPromotionApplicationService {

    public static final String FALLBACK_REASON_REAL_PROMOTION_WRITE_DISABLED = "REAL_PROMOTION_WRITE_DISABLED";

    private final CopyPromotionSupportPort copyPromotionSupportPort;
    private final ConfigDomainFacade configDomainFacade;
    @Value("${douyin.real.promotion-write-enabled:false}")
    private boolean realPromotionWriteEnabled;
    @Value("${douyin.real.allow-promotion-write:false}")
    private boolean allowRealPromotionWrite;

    public CopyPromotionApplicationService(
            CopyPromotionSupportPort copyPromotionSupportPort,
            ConfigDomainFacade configDomainFacade) {
        this.copyPromotionSupportPort = copyPromotionSupportPort;
        this.configDomainFacade = configDomainFacade;
    }

    /**
     * 复制推广简介主入口。
     *
     * <ol>
     *   <li>保证 snapshot 存在并加载商品操作状态</li>
     *   <li>要求商品已加入商品库</li>
     *   <li>读取业务状态，仅 APPROVED / ASSIGNED / LINKED 可继续</li>
     *   <li>若真实转链写入未开启，返回降级文案（无链接）</li>
     *   <li>否则调转链 Port，组装含链接的复制文本</li>
     * </ol>
     */
    @Transactional(rollbackFor = Exception.class)
    public PromotionLinkCopyResult copyPromotion(
            String activityId,
            String productId,
            UUID userId,
            UUID deptId,
            String externalUniqueId,
            Integer promotionScene,
            boolean needShortLink,
            String scene,
            String talentId,
            String idempotencyKey) {
        return copyPromotion(activityId, productId, userId, deptId, externalUniqueId, promotionScene,
                needShortLink, scene, talentId, idempotencyKey, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public PromotionLinkCopyResult copyPromotion(
            String activityId,
            String productId,
            UUID userId,
            UUID deptId,
            String externalUniqueId,
            Integer promotionScene,
            boolean needShortLink,
            String scene,
            String talentId,
            String idempotencyKey,
            AttributionOwnerType attributionOwnerType) {
        return copyPromotion(
                activityId,
                productId,
                userId,
                deptId,
                externalUniqueId,
                promotionScene,
                needShortLink,
                scene,
                talentId,
                idempotencyKey,
                attributionOwnerType,
                realPromotionWriteEnabled,
                allowRealPromotionWrite);
    }

    @Transactional(rollbackFor = Exception.class)
    public PromotionLinkCopyResult copyPromotion(
            String activityId,
            String productId,
            UUID userId,
            UUID deptId,
            String externalUniqueId,
            Integer promotionScene,
            boolean needShortLink,
            String scene,
            String talentId,
            String idempotencyKey,
            boolean realPromotionWriteEnabled,
            boolean allowRealPromotionWrite) {
        return copyPromotion(activityId, productId, userId, deptId, externalUniqueId, promotionScene,
                needShortLink, scene, talentId, idempotencyKey, null,
                realPromotionWriteEnabled, allowRealPromotionWrite);
    }

    @Transactional(rollbackFor = Exception.class)
    public PromotionLinkCopyResult copyPromotion(
            String activityId,
            String productId,
            UUID userId,
            UUID deptId,
            String externalUniqueId,
            Integer promotionScene,
            boolean needShortLink,
            String scene,
            String talentId,
            String idempotencyKey,
            AttributionOwnerType attributionOwnerType,
            boolean realPromotionWriteEnabled,
            boolean allowRealPromotionWrite) {

        CopyPromotionSupportPort.Context ctx = copyPromotionSupportPort.prepareCopyPromotionContext(
                activityId, productId, "复制推广简介");

        if (!isRealPromotionWriteAllowed(realPromotionWriteEnabled, allowRealPromotionWrite)) {
            String text = CopyTextPolicy.render(
                    configDomainFacade, ctx.snapshot(), ctx.state(), null);
            return new PromotionLinkCopyResult(
                    text,
                    false,
                    null,
                    null,
                    FALLBACK_REASON_REAL_PROMOTION_WRITE_DISABLED,
                    realPromotionWriteEnabled,
                    allowRealPromotionWrite
            );
        }

        CopyPromotionSupportPort.GeneratedPromotionLink result = attributionOwnerType == null
                ? copyPromotionSupportPort.generatePromotionLinkForCopy(
                        activityId, productId, userId, deptId, externalUniqueId, promotionScene,
                        needShortLink, scene, talentId, idempotencyKey)
                : copyPromotionSupportPort.generatePromotionLinkForCopy(
                        activityId, productId, userId, deptId, externalUniqueId, promotionScene,
                        needShortLink, scene, talentId, idempotencyKey, attributionOwnerType);
        String promotionLink = CopyTextPolicy.firstText(result.shortLink(), result.promoteLink());
        String text = CopyTextPolicy.render(
                configDomainFacade, ctx.snapshot(), ctx.state(), promotionLink);
        return new PromotionLinkCopyResult(
                text,
                true,
                promotionLink,
                result.pickSource(),
                null,
                realPromotionWriteEnabled,
                allowRealPromotionWrite
        );
    }

    private static boolean isRealPromotionWriteAllowed(boolean enabled, boolean allowed) {
        return enabled && allowed;
    }
}
