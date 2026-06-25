package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.colonel.saas.common.exception.OptimisticLockSupport;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.domain.product.policy.ProductPinPolicy;
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
 * 招商商品置顶服务（P-05 验收项）。
 * <p>
 * 允许招商人员将已入商品库的规格置顶，在商品列表中获得更高的展示排序。
 * 置顶规则：
 * <ul>
 *   <li>每位用户最多同时置顶 {@value ProductPinPolicy#MAX_PINNED_PER_USER} 个规格</li>
 *   <li>每个置顶有效期 {@value ProductPinPolicy#PIN_HOURS} 小时，过期自动失效</li>
 *   <li>置顶人可以取消自己的置顶；管理员可以取消任何人的置顶</li>
 *   <li>同一规格重复置顶会刷新有效期（不占用额外配额）</li>
 * </ul>
 * </p>
 * <p>
 * 置顶信息存储在 {@link ProductOperationState} 的 pinnedAt / pinnedUntil / pinnedBy 三个字段中，
 * 通过定时任务 {@link #expirePinnedProducts()} 清理已过期的置顶标记。
 * </p>
 *
 * @see ProductOperationState
 * @see ProductService
 */
@Service
public class ProductPinService {

    /** @deprecated 使用 {@link ProductPinPolicy#MAX_PINNED_PER_USER} */
    @Deprecated
    public static final int MAX_PINNED_PER_USER = ProductPinPolicy.MAX_PINNED_PER_USER;
    /** @deprecated 使用 {@link ProductPinPolicy#PIN_HOURS} */
    @Deprecated
    public static final int PIN_HOURS = ProductPinPolicy.PIN_HOURS;

    /** 商品运营状态 Mapper */
    private final ProductOperationStateMapper operationStateMapper;
    /** 商品快照 Mapper（用于获取商品标题、封面等展示信息） */
    private final ProductSnapshotMapper productSnapshotMapper;

    /**
     * 构造注入依赖。
     *
     * @param operationStateMapper  商品运营状态 Mapper
     * @param productSnapshotMapper 商品快照 Mapper
     */
    public ProductPinService(
            ProductOperationStateMapper operationStateMapper,
            ProductSnapshotMapper productSnapshotMapper) {
        this.operationStateMapper = operationStateMapper;
        this.productSnapshotMapper = productSnapshotMapper;
    }

    /**
     * 置顶商品规格。
     * <p>
     * 业务规则：
     * <ul>
     *   <li>必须是已登录用户</li>
     *   <li>同一规格已处于置顶状态时，刷新有效期（不占用额外配额）</li>
     *   <li>非置顶状态的规格，当用户已达置顶上限时抛出异常</li>
     *   <li>使用乐观锁确保并发安全</li>
     * </ul>
     * </p>
     *
     * @param activityId 活动 ID（团长活动）
     * @param productId  商品 ID（抖店商品）
     * @param userId     操作用户 ID
     * @return 更新后的商品运营状态（包含新的置顶时间）
     * @throws BusinessException 当用户为 null、商品状态不存在或置顶数量超限时
     */
    @Transactional(rollbackFor = Exception.class)
    public ProductOperationState pin(String activityId, String productId, UUID userId) {
        if (userId == null) {
            throw BusinessException.param("置顶操作需要登录用户");
        }
        ProductOperationState state = requireState(activityId, productId);
        LocalDateTime now = LocalDateTime.now();
        // 查询当前用户已有的有效置顶数量
        long activePins = operationStateMapper.selectCount(new LambdaQueryWrapper<ProductOperationState>()
                .eq(ProductOperationState::getPinnedBy, userId)
                .gt(ProductOperationState::getPinnedUntil, now)
                .eq(ProductOperationState::getDeleted, 0));
        // 判断该规格是否已处于置顶状态（已置顶的刷新有效期不占配额）
        boolean alreadyPinned = ProductPinPolicy.isPinned(state, now);
        if (ProductPinPolicy.exceedsQuota(activePins, alreadyPinned)) {
            throw BusinessException.stateInvalid("置顶数量已达上限（最多 " + ProductPinPolicy.MAX_PINNED_PER_USER + " 个）");
        }
        state.setPinnedAt(now);
        state.setPinnedUntil(ProductPinPolicy.pinExpiresAt(now));
        state.setPinnedBy(userId);
        OptimisticLockSupport.requireUpdated(operationStateMapper.updateById(state));
        return state;
    }

    /**
     * 取消置顶。
     * <p>
     * 权限校验：仅置顶人本人或管理员可取消置顶。清空 pinnedAt / pinnedUntil / pinnedBy 三个字段。
     * </p>
     *
     * @param activityId 活动 ID
     * @param productId  商品 ID
     * @param userId     操作用户 ID（null 时跳过权限校验，视为管理员操作）
     * @return 更新后的商品运营状态
     * @throws BusinessException 当商品状态不存在或非置顶人尝试取消时
     */
    @Transactional(rollbackFor = Exception.class)
    public ProductOperationState unpin(String activityId, String productId, UUID userId) {
        ProductOperationState state = requireState(activityId, productId);
        if (!ProductPinPolicy.canUnpin(state.getPinnedBy(), userId)) {
            throw BusinessException.forbidden("仅置顶人或管理员可取消置顶");
        }
        state.setPinnedAt(null);
        state.setPinnedUntil(null);
        state.setPinnedBy(null);
        OptimisticLockSupport.requireUpdated(operationStateMapper.updateById(state));
        return state;
    }

    /**
     * 查询指定用户的当前有效置顶商品列表。
     * <p>
     * 返回按置顶时间倒序排列的 {@link PinnedProductVO} 列表，包含商品标题和封面图。
     * 标题优先取快照中的 title，降级使用 productId。
     * </p>
     *
     * @param userId 用户 ID（null 时返回空列表）
     * @return 有效置顶商品视图列表（可能为空列表）
     */
    public List<PinnedProductVO> listPinnedProducts(UUID userId) {
        if (userId == null) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        // 查询该用户所有尚未过期的有效置顶
        List<ProductOperationState> states = operationStateMapper.selectList(new LambdaQueryWrapper<ProductOperationState>()
                .eq(ProductOperationState::getPinnedBy, userId)
                .gt(ProductOperationState::getPinnedUntil, now)
                .eq(ProductOperationState::getDeleted, 0)
                .orderByDesc(ProductOperationState::getPinnedAt));
        if (states == null || states.isEmpty()) {
            return List.of();
        }
        // 批量加载关联的商品快照（标题、封面图等展示信息）
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
        // 按置顶时间倒序排列，最新置顶的排在前面
        result.sort(Comparator.comparing(PinnedProductVO::pinnedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    /**
     * 批量加载商品快照。
     * <p>
     * 逐条查询各运营状态关联的商品快照，以 activityId:productId 为 key 组装 Map。
     * </p>
     *
     * @param states 运营状态列表
     * @return 快照 Map，key 为 "activityId:productId"
     */
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

    /**
     * 拼接快照 Map 的 key。
     *
     * @param activityId 活动 ID
     * @param productId  商品 ID
     * @return "activityId:productId" 格式的 key
     */
    private static String stateKey(String activityId, String productId) {
        return activityId + ":" + productId;
    }

    /**
     * 返回第一个非空白值，若 primary 为空白则返回 fallback。
     *
     * @param primary  优先值
     * @param fallback 降级值
     * @return 非空白的值
     */
    private static String firstNonBlank(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary : fallback;
    }

    /**
     * 判断商品运营状态是否处于有效置顶状态。
     * <p>
     * 静态工具方法，供其他服务（如商品排序逻辑）直接调用，无需注入本服务实例。
     * </p>
     *
     * @param state 商品运营状态（null 时返回 false）
     * @param now   当前时间（null 时使用 {@link LocalDateTime#now()}）
     * @return true 表示仍在置顶有效期内
     */
    public static boolean isPinned(ProductOperationState state, LocalDateTime now) {
        return ProductPinPolicy.isPinned(state, now);
    }

    /**
     * 清理已过期的置顶标记（使用当前时间）。
     * <p>
     * 通常由定时任务调用，避免 pinned_until 已过期的记录长期残留。
     * 使用批量 UPDATE 将过期记录的 pinnedAt / pinnedUntil / pinnedBy 置为 null。
     * </p>
     *
     * @return 受影响的行数
     */
    @Transactional(rollbackFor = Exception.class)
    public int expirePinnedProducts() {
        return expirePinnedProducts(LocalDateTime.now());
    }

    /**
     * 清理已过期的置顶标记（指定时间版本）。
     * <p>
     * 将所有 pinnedUntil &lt;= cutoff 且未删除的记录的置顶字段清空。
     * </p>
     *
     * @param now 截止时间（null 时使用当前时间）
     * @return 受影响的行数
     */
    @Transactional(rollbackFor = Exception.class)
    public int expirePinnedProducts(LocalDateTime now) {
        LocalDateTime cutoff = now == null ? LocalDateTime.now() : now;
        UpdateWrapper<ProductOperationState> update = new UpdateWrapper<ProductOperationState>()
                .set("pinned_at", null)
                .set("pinned_until", null)
                .set("pinned_by", null)
                .isNotNull("pinned_until")
                .le("pinned_until", cutoff)
                .eq("deleted", 0);
        return operationStateMapper.update(null, update);
    }

    /**
     * 查询并校验商品运营状态是否存在。
     *
     * @param activityId 活动 ID
     * @param productId  商品 ID
     * @return 存在的商品运营状态
     * @throws BusinessException 当商品运营状态不存在时
     */
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
