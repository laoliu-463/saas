package com.colonel.saas.domain.product.facade;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.common.enums.ProductBizStatus;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.ProductDisplayStatus;
import com.colonel.saas.domain.product.facade.dto.ActivityBriefDTO;
import com.colonel.saas.domain.product.facade.dto.ActivityProductForSampleDTO;
import com.colonel.saas.domain.product.facade.dto.PartnerBriefDTO;
import com.colonel.saas.domain.product.facade.dto.ProductBriefDTO;
import com.colonel.saas.domain.product.facade.dto.ProductDisplayInfoDTO;
import com.colonel.saas.domain.product.facade.dto.ProductOwnerDTO;
import com.colonel.saas.entity.ColonelPartner;
import com.colonel.saas.entity.ColonelsettlementActivity;
import com.colonel.saas.entity.Product;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.mapper.ColonelPartnerMapper;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import com.colonel.saas.service.ColonelPartnerSyncService;
import com.colonel.saas.service.ProductService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@link ProductDomainFacade} 遗留实现：委派现有 ProductService / Mapper，零调用方迁移。
 */
@Service
public class LegacyProductDomainFacade implements ProductDomainFacade {

    private static final int UPSTREAM_PRODUCT_STATUS_PROMOTING = 1;
    private static final int PARTNER_QUERY_LIMIT = 200;

    private final ProductService productService;
    private final ProductSnapshotMapper productSnapshotMapper;
    private final ProductOperationStateMapper productOperationStateMapper;
    private final ColonelsettlementActivityMapper colonelActivityMapper;
    private final ColonelPartnerMapper colonelPartnerMapper;
    private final ColonelPartnerSyncService colonelPartnerSyncService;

    public LegacyProductDomainFacade(
            ProductService productService,
            ProductSnapshotMapper productSnapshotMapper,
            ProductOperationStateMapper productOperationStateMapper,
            ColonelsettlementActivityMapper colonelActivityMapper,
            ColonelPartnerMapper colonelPartnerMapper,
            ColonelPartnerSyncService colonelPartnerSyncService) {
        this.productService = productService;
        this.productSnapshotMapper = productSnapshotMapper;
        this.productOperationStateMapper = productOperationStateMapper;
        this.colonelActivityMapper = colonelActivityMapper;
        this.colonelPartnerMapper = colonelPartnerMapper;
        this.colonelPartnerSyncService = colonelPartnerSyncService;
    }

    @Override
    public ActivityProductForSampleDTO getActivityProductForSample(UUID relationId) {
        if (relationId == null) {
            return null;
        }
        Product legacyProduct = productService.getById(relationId);
        if (legacyProduct == null) {
            return null;
        }
        ProductSnapshot snapshot = productSnapshotMapper.selectById(relationId);
        if (snapshot == null) {
            return null;
        }
        ProductOperationState state = loadOperationState(snapshot.getActivityId(), snapshot.getProductId());
        ProductOwnerDTO owner = resolveOwner(snapshot, state);
        return new ActivityProductForSampleDTO(
                snapshot.getId(),
                snapshot.getActivityId(),
                snapshot.getProductId(),
                snapshot.getTitle(),
                snapshot.getCover(),
                snapshot.getPrice(),
                snapshot.getPriceText(),
                snapshot.getShopId(),
                snapshot.getShopName(),
                snapshot.getDetailUrl(),
                snapshot.getPromotionStartTime(),
                snapshot.getPromotionEndTime(),
                state == null ? null : state.getDisplayStatus(),
                state == null ? null : state.getSelectedToLibrary(),
                isVisibleForSample(snapshot, state),
                owner == null ? null : owner.ownerUserId()
        );
    }

    @Override
    public ProductOwnerDTO getRecruiterForActivityProduct(String activityId, String productId) {
        ProductSnapshot snapshot = loadSnapshot(activityId, productId);
        ProductOperationState state = loadOperationState(activityId, productId);
        return resolveOwner(snapshot, state, activityId, productId);
    }

    @Override
    public ProductBriefDTO getProductBrief(String productId) {
        if (!StringUtils.hasText(productId)) {
            return null;
        }
        ProductSnapshot snapshot = productSnapshotMapper.selectOne(new LambdaQueryWrapper<ProductSnapshot>()
                .eq(ProductSnapshot::getProductId, productId.trim())
                .orderByDesc(ProductSnapshot::getSyncTime)
                .last("LIMIT 1"));
        return toProductBrief(snapshot);
    }

    @Override
    public ActivityBriefDTO getActivityBrief(String activityId) {
        if (!StringUtils.hasText(activityId)) {
            return null;
        }
        return toActivityBrief(colonelActivityMapper.selectByActivityId(activityId.trim()));
    }

    @Override
    public PartnerBriefDTO getPartnerBrief(String partnerId) {
        if (!StringUtils.hasText(partnerId)) {
            return null;
        }
        String normalized = partnerId.trim();
        ColonelPartner partner = findPartner(normalized);
        return toPartnerBrief(partner);
    }

    @Override
    public Map<String, ProductBriefDTO> batchGetProductBrief(Collection<String> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        List<String> distinctIds = productIds.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (distinctIds.isEmpty()) {
            return Map.of();
        }
        return productSnapshotMapper.selectList(new LambdaQueryWrapper<ProductSnapshot>()
                        .in(ProductSnapshot::getProductId, distinctIds)
                        .orderByDesc(ProductSnapshot::getSyncTime))
                .stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        ProductSnapshot::getProductId,
                        this::toProductBrief,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    @Override
    public List<PartnerBriefDTO> listPartners(String keyword) {
        return colonelPartnerSyncService.listByNameKeyword(keyword, PARTNER_QUERY_LIMIT).stream()
                .map(this::toPartnerBrief)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public boolean checkProductVisibleForSample(UUID relationId) {
        if (relationId == null) {
            return false;
        }
        try {
            if (productService.getById(relationId) == null) {
                return false;
            }
        } catch (BusinessException ex) {
            return false;
        }
        ProductSnapshot snapshot = productSnapshotMapper.selectById(relationId);
        if (snapshot == null) {
            return false;
        }
        ProductOperationState state = loadOperationState(snapshot.getActivityId(), snapshot.getProductId());
        return isVisibleForSample(snapshot, state);
    }

    @Override
    public ProductOwnerDTO getProductOwner(UUID relationId) {
        if (relationId == null) {
            return null;
        }
        ProductSnapshot snapshot = productSnapshotMapper.selectById(relationId);
        if (snapshot == null) {
            return null;
        }
        ProductOperationState state = loadOperationState(snapshot.getActivityId(), snapshot.getProductId());
        return resolveOwner(snapshot, state);
    }

    @Override
    public ProductDisplayInfoDTO getProductDisplayInfo(UUID relationId) {
        if (relationId == null) {
            return null;
        }
        ProductSnapshot snapshot = productSnapshotMapper.selectById(relationId);
        if (snapshot == null) {
            return null;
        }
        ProductOperationState state = loadOperationState(snapshot.getActivityId(), snapshot.getProductId());
        return new ProductDisplayInfoDTO(
                snapshot.getId(),
                snapshot.getActivityId(),
                snapshot.getProductId(),
                snapshot.getStatus(),
                snapshot.getStatusText(),
                state == null ? null : state.getDisplayStatus(),
                state == null ? null : state.getSelectedToLibrary(),
                state == null ? null : state.getManualDisabled(),
                state == null ? null : state.getAuditStatus(),
                state == null ? null : state.getBizStatus(),
                state == null ? null : state.getHiddenReason(),
                isVisibleForSample(snapshot, state)
        );
    }

    private ProductSnapshot loadSnapshot(String activityId, String productId) {
        if (!StringUtils.hasText(activityId) || !StringUtils.hasText(productId)) {
            return null;
        }
        return productSnapshotMapper.selectOne(new LambdaQueryWrapper<ProductSnapshot>()
                .eq(ProductSnapshot::getActivityId, activityId.trim())
                .eq(ProductSnapshot::getProductId, productId.trim())
                .last("LIMIT 1"));
    }

    private ProductOperationState loadOperationState(String activityId, String productId) {
        if (!StringUtils.hasText(activityId) || !StringUtils.hasText(productId)) {
            return null;
        }
        return productOperationStateMapper.selectOne(new LambdaQueryWrapper<ProductOperationState>()
                .eq(ProductOperationState::getActivityId, activityId.trim())
                .eq(ProductOperationState::getProductId, productId.trim())
                .last("LIMIT 1"));
    }

    private ProductOwnerDTO resolveOwner(ProductSnapshot snapshot, ProductOperationState state) {
        if (snapshot == null) {
            return null;
        }
        return resolveOwner(snapshot, state, snapshot.getActivityId(), snapshot.getProductId());
    }

    private ProductOwnerDTO resolveOwner(
            ProductSnapshot snapshot,
            ProductOperationState state,
            String activityId,
            String productId) {
        UUID relationId = snapshot == null ? null : snapshot.getId();
        if (state != null && state.getAssigneeId() != null) {
            return new ProductOwnerDTO(
                    relationId,
                    activityId,
                    productId,
                    state.getAssigneeId(),
                    null,
                    "PRODUCT_ASSIGNEE");
        }
        if (!StringUtils.hasText(activityId)) {
            return new ProductOwnerDTO(relationId, activityId, productId, null, null, "UNASSIGNED");
        }
        ColonelsettlementActivity activity = colonelActivityMapper.selectByActivityId(activityId.trim());
        if (activity != null && activity.getRecruiterUserId() != null) {
            return new ProductOwnerDTO(
                    relationId,
                    activityId,
                    productId,
                    activity.getRecruiterUserId(),
                    activity.getRecruiterDeptId(),
                    "ACTIVITY_RECRUITER");
        }
        return new ProductOwnerDTO(relationId, activityId, productId, null, null, "UNASSIGNED");
    }

    private boolean isVisibleForSample(ProductSnapshot snapshot, ProductOperationState state) {
        return snapshot != null
                && Integer.valueOf(UPSTREAM_PRODUCT_STATUS_PROMOTING).equals(snapshot.getStatus())
                && state != null
                && ProductDisplayStatus.DISPLAYING.name().equals(state.getDisplayStatus())
                && Boolean.TRUE.equals(state.getSelectedToLibrary())
                && !isLocalRejectedState(state)
                && !Boolean.TRUE.equals(state.getManualDisabled());
    }

    private boolean isLocalRejectedState(ProductOperationState state) {
        if (state == null) {
            return false;
        }
        if (Integer.valueOf(3).equals(state.getAuditStatus())) {
            return true;
        }
        try {
            return ProductBizStatus.REJECTED == ProductBizStatus.fromCode(state.getBizStatus());
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private ProductBriefDTO toProductBrief(ProductSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return new ProductBriefDTO(
                snapshot.getId(),
                snapshot.getProductId(),
                snapshot.getTitle(),
                snapshot.getCover(),
                snapshot.getPrice(),
                snapshot.getPriceText(),
                snapshot.getShopId(),
                snapshot.getShopName(),
                snapshot.getStatus(),
                snapshot.getStatusText(),
                snapshot.getCategoryName(),
                snapshot.getDetailUrl()
        );
    }

    private ActivityBriefDTO toActivityBrief(ColonelsettlementActivity activity) {
        if (activity == null) {
            return null;
        }
        return new ActivityBriefDTO(
                activity.getActivityId(),
                activity.getName(),
                activity.getShopId(),
                activity.getShopName(),
                activity.getColonelBuyinId(),
                activity.getStatus(),
                activity.getActivityStatusCode(),
                activity.getActivityStatusText(),
                activity.getStartTime(),
                activity.getEndTime(),
                activity.getRecruiterUserId(),
                activity.getRecruiterDeptId()
        );
    }

    private PartnerBriefDTO toPartnerBrief(ColonelPartner partner) {
        if (partner == null) {
            return null;
        }
        String id = partner.getId() == null ? null : partner.getId().toString();
        return new PartnerBriefDTO(
                id,
                partner.getId(),
                partner.getColonelBuyinId(),
                partner.getColonelName(),
                partner.getContactName(),
                partner.getContactPhone(),
                partner.getAvatarUrl(),
                partner.getSource()
        );
    }

    private ColonelPartner findPartner(String partnerId) {
        try {
            UUID id = UUID.fromString(partnerId);
            return colonelPartnerMapper.selectById(id);
        } catch (IllegalArgumentException ignored) {
            return colonelPartnerMapper.selectOne(new LambdaQueryWrapper<ColonelPartner>()
                    .eq(ColonelPartner::getColonelBuyinId, partnerId)
                    .last("LIMIT 1"));
        }
    }
}
