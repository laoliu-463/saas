package com.colonel.saas.domain.product.application;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.product.facade.ProductDomainFacade;
import com.colonel.saas.dto.product.QuickSampleApplyRequest;
import com.colonel.saas.dto.product.QuickSampleApplyResponse;
import com.colonel.saas.service.ProductQuickSampleService;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 商品快速寄样应用层（DDD-PRODUCT-003 Batch3 Replace）。
 *
 * <p>Controller 写路径统一经本服务；开关 {@code ddd.refactor.product-facade.enabled=true}
 * 且根开关开启时，先走 {@link ProductDomainFacade} 存在性检查，再委派 {@link ProductQuickSampleService}。</p>
 */
@Service
public class ProductQuickSampleApplicationService {

    private final ProductQuickSampleService productQuickSampleService;
    private final ProductDomainFacade productDomainFacade;
    private final DddRefactorProperties dddRefactorProperties;

    public ProductQuickSampleApplicationService(
            ProductQuickSampleService productQuickSampleService,
            ProductDomainFacade productDomainFacade,
            DddRefactorProperties dddRefactorProperties) {
        this.productQuickSampleService = productQuickSampleService;
        this.productDomainFacade = productDomainFacade;
        this.dddRefactorProperties = dddRefactorProperties;
    }

    /** 是否启用 Facade 路由（需同时打开根开关与 product-facade 子开关）。 */
    public boolean isRoutingEnabled() {
        return dddRefactorProperties.isEnabled()
                && dddRefactorProperties.getProductFacade().isEnabled();
    }

    public QuickSampleApplyResponse applyQuickSample(
            UUID relationId,
            QuickSampleApplyRequest request,
            UUID userId,
            UUID deptId,
            Object roleCodes) {
        if (isRoutingEnabled()) {
            assertProductExistsViaFacade(relationId);
        }
        return productQuickSampleService.applyQuickSample(relationId, request, userId, deptId, roleCodes);
    }

    private void assertProductExistsViaFacade(UUID relationId) {
        if (!productDomainFacade.existsById(relationId)) {
            throw BusinessException.notFound("商品不存在或已不在商品库，请刷新商品后重试");
        }
    }
}
