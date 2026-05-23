package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.common.exception.OptimisticLockSupport;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import com.colonel.saas.vo.PinnedProductVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 招商商品置顶（P-05）：24 小时有效，每位招商最多 10 个规格。
 */
@Service
public class ProductPinService {

    public static final int MAX_PINNED_PER_USER = 10;
    public static final int PIN_HOURS = 24;

    private final ProductOperationStateMapper operationStateMapper;
    private final ProductSnapshotMapper productSnapshotMapper;

    public ProductPinService(
            ProductOperationStateMapper operationStateMapper,
            ProductSnapshotMapper productSnapshotMapper) {
        this.operationStateMapper = operationStateMapper;
        this.productSnapshotMapper = productSnapshotMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public ProductOperationState pin(String activityId, String productId, UUID userId) {
        if (userId == null) {
            throw BusinessException.param("置顶操作需要登录用户");
        }
        ProductOperationState state = requireState(activityId, productId);
        LocalDateTime now = LocalDateTime.now();
        long activePins = operationStateMapper.selectCount(new LambdaQueryWrapper<ProductOperationState>()
                .eq(ProductOperationState::getPinnedBy, userId)
                .gt(ProductOperationState::getPinnedUntil, now)
                .eq(ProductOperationState::getDeleted, 0));
        boolean alreadyPinned = state.getPinnedUntil() != null && state.getPinnedUntil().isAfter(now);
        if (!alreadyPinned && activePins >= MAX_PINNED_PER_USER) {
            throw BusinessException.stateInvalid("置顶数量已达上限（最多 " + MAX_PINNED_PER_USER + " 个）");
        }
        state.setPinnedAt(now);
        state.setPinnedUntil(now.plusHours(PIN_HOURS));
        state.setPinnedBy(userId);
        OptimisticLockSupport.requireUpdated(operationStateMapper.updateById(state));
        return state;
    }

    @Transactional(rollbackFor = Exception.class)
    public ProductOperationState unpin(String activityId, String productId, UUID userId) {
        ProductOperationState state = requireState(activityId, productId);
        if (userId != null && state.getPinnedBy() != null && !userId.equals(state.getPinnedBy())) {
            throw BusinessException.forbidden("仅置顶人或管理员可取消置顶");
        }
        state.setPinnedAt(null);
        state.setPinnedUntil(null);
        state.setPinnedBy(null);
        OptimisticLockSupport.requireUpdated(operationStateMapper.updateById(state));
        return state;
    }

    public List<PinnedProductVO> listPinnedProducts(UUID userId) {
        if (userId == null) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        List<ProductOperationState> states = operationStateMapper.selectList(new LambdaQueryWrapper<ProductOperationState>()
                .eq(ProductOperationState::getPinnedBy, userId)
                .gt(ProductOperationState::getPinnedUntil, now)
                .eq(ProductOperationState::getDeleted, 0)
                .orderByDesc(ProductOperationState::getPinnedAt));
        if (states == null || states.isEmpty()) {
            return List.of();
        }
        Map<String, ProductSnapshot> snapshotMap = loadSnapshots(states);
        List<PinnedProductVO> result = new ArrayList<>();
        for (ProductOperationState state : states) {
            ProductSnapshot snapshot = snapshotMap.get(stateKey(state.getActivityId(), state.getProductId()));
            result.add(new PinnedProductVO(
                    state.getActivityId(),
                    state.getProductId(),
                    snapshot == null ? state.getProductId() : firstNonBlank(snapshot.getTitle(), state.getProductId()),
                    snapshot == null ? null : snapshot.getCover(),
                    state.getPinnedAt(),
                    state.getPinnedUntil()));
        }
        result.sort(Comparator.comparing(PinnedProductVO::pinnedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    private Map<String, ProductSnapshot> loadSnapshots(List<ProductOperationState> states) {
        Map<String, ProductSnapshot> snapshotMap = new HashMap<>();
        if (states == null) {
            return snapshotMap;
        }
        for (ProductOperationState state : states) {
            if (state == null || !StringUtils.hasText(state.getActivityId()) || !StringUtils.hasText(state.getProductId())) {
                continue;
            }
            ProductSnapshot snapshot = productSnapshotMapper.selectOne(new LambdaQueryWrapper<ProductSnapshot>()
                    .eq(ProductSnapshot::getActivityId, state.getActivityId())
                    .eq(ProductSnapshot::getProductId, state.getProductId())
                    .eq(ProductSnapshot::getDeleted, 0)
                    .last("limit 1"));
            if (snapshot != null) {
                snapshotMap.put(stateKey(state.getActivityId(), state.getProductId()), snapshot);
            }
        }
        return snapshotMap;
    }

    private static String stateKey(String activityId, String productId) {
        return activityId + ":" + productId;
    }

    private static String firstNonBlank(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary : fallback;
    }

    public static boolean isPinned(ProductOperationState state, LocalDateTime now) {
        return state != null
                && state.getPinnedUntil() != null
                && state.getPinnedUntil().isAfter(now == null ? LocalDateTime.now() : now);
    }

    private ProductOperationState requireState(String activityId, String productId) {
        ProductOperationState state = operationStateMapper.selectOne(new LambdaQueryWrapper<ProductOperationState>()
                .eq(ProductOperationState::getActivityId, activityId)
                .eq(ProductOperationState::getProductId, productId)
                .eq(ProductOperationState::getDeleted, 0)
                .last("limit 1"));
        if (state == null) {
            throw BusinessException.notFound("商品运营状态不存在");
        }
        return state;
    }
}
