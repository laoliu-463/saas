package com.colonel.saas.domain.product.application;

import com.colonel.saas.domain.config.facade.ConfigDomainFacade;
import com.colonel.saas.domain.product.application.dto.PromotionLinkCopyResult;
import com.colonel.saas.domain.product.policy.CopyTextPolicy;
import com.colonel.saas.domain.product.port.DouyinConvertPort;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
import com.colonel.saas.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 复制推广简介应用层（DDD-PRODUCT-004）。
 *
 * <p>把 {@code ProductService.generatePromotionLinkCopy} 的业务编排抽到本服务：
 * 状态前置校验 → 转链 Port 调用 → 复制文本渲染 → 返回结果。</p>
 *
 * <p>纯文本渲染由 {@link CopyTextPolicy} 完成；上游 SDK 由 {@link DouyinConvertPort} 抽象。
 * 实际快照/状态读取和转链执行仍由 {@link ProductService} 负责（避免重复实现和循环依赖）。</p>
 */
@Service
public class CopyPromotionApplicationService {

    public static final String FALLBACK_REASON_REAL_PROMOTION_WRITE_DISABLED = "REAL_PROMOTION_WRITE_DISABLED";

    private final ProductService productService;
    private final DouyinConvertPort douyinConvertPort;
    private final ConfigDomainFacade configDomainFacade;

    public CopyPromotionApplicationService(
            @Autowired @Lazy ProductService productService,
            DouyinConvertPort douyinConvertPort,
            ConfigDomainFacade configDomainFacade) {
        this.productService = productService;
        this.douyinConvertPort = douyinConvertPort;
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

        Context ctx = productService.prepareCopyPromotionContext(activityId, productId, "复制推广简介");

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

        DouyinPromotionGateway.PromotionLinkResult result = productService.generatePromotionLinkInternal(
                activityId,
                productId,
                userId,
                deptId,
                externalUniqueId,
                promotionScene,
                needShortLink,
                scene,
                talentId);
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

    /**
     * 复制推广上下文（由 ProductService 在 prepareCopyPromotionContext 中装配）。
     */
    public record Context(ProductSnapshot snapshot, ProductOperationState state) {
    }
}
