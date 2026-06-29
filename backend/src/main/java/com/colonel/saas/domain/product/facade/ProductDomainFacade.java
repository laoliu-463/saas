package com.colonel.saas.domain.product.facade;

import com.colonel.saas.domain.product.facade.dto.ProductOrderDisplayDTO;
import com.colonel.saas.domain.product.facade.dto.ProductReadDTO;
import com.colonel.saas.domain.product.facade.dto.ProductSnapshotOrderDisplayDTO;
import com.colonel.saas.domain.product.facade.dto.ProductSnapshotReadDTO;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 商品域只读门面（DDD-PRODUCT-001）。
 * <p>
 * 寄样域、订单域、业绩域等应通过本接口读取商品主数据与快照，
 * 禁止新增跨域 {@code ProductMapper} / {@code ProductSnapshotMapper} 注入。
 * 第一版内部委派既有 Mapper，不改变线上行为。
 * </p>
 */
public interface ProductDomainFacade {

    /** 按内部主键查询商品，不存在时返回 null。 */
    ProductReadDTO findProductById(UUID productId);

    /** 按抖店商品 ID（product.product_id）查询，不存在时返回 null。 */
    ProductReadDTO findProductByExternalId(String externalProductId);

    /** 按快照主键查询，不存在时返回 null。 */
    ProductSnapshotReadDTO findSnapshotById(UUID snapshotId);

    /** 商品是否存在（按内部主键）。 */
    boolean existsById(UUID productId);

    /** 寄样域按商品或快照主键读取商品，必要时从快照物化商品记录。 */
    ProductReadDTO findOrMaterializeSampleProduct(UUID productId);

    /** 寄样创建前校验商品是否已加入商品库；无快照时保持旧行为，视为可继续。 */
    boolean isSelectedToLibraryForSample(UUID productId);

    /** 寄样权限校验：商品运营状态是否分配给指定用户。 */
    boolean isSampleProductAssignedToUser(UUID productId, UUID userId);

    /** 寄样查询过滤：按商品名称或外部商品 ID 模糊匹配商品主键。 */
    Set<UUID> findProductIdsByKeyword(String keyword, long limit);

    /** 寄样查询过滤：按店铺名称或店铺 ID 匹配商品快照主键。 */
    Set<UUID> findProductSnapshotIdsByShopKeyword(String keyword, long limit);

    /** 批量加载商品读模型，返回 productId → 商品。 */
    Map<UUID, ProductReadDTO> loadProductsByIds(Collection<UUID> ids);

    /** 订单列表展示补全：按活动/商品维度读取商品快照。 */
    List<ProductSnapshotOrderDisplayDTO> loadOrderDisplaySnapshots(
            Collection<String> productIds,
            Collection<String> activityIds);

    /** 订单列表展示补全：按订单商品 ID 读取商品主数据，匹配 product_id 或 outer_product_id。 */
    List<ProductOrderDisplayDTO> loadOrderDisplayProducts(Collection<String> productIds);

    /** 通过商品快照解析招商负责人。 */
    UUID findProductSnapshotAssigneeId(UUID productId);

    /** 解析寄样单关联商品的抖店源商品 ID。 */
    String resolveSampleSourceProductId(UUID productId);

    /**
     * 批量加载商品名称，返回 productId → name 映射。
     * 自动过滤 null 和重复 ID；缺失记录不包含在结果中。
     */
    Map<UUID, String> loadProductNamesByIds(Collection<UUID> ids);

    /** 活动+商品维度的实际招商负责人（product_operation_state.assignee_id）。 */
    UUID findProductAssigneeId(String activityId, String externalProductId);

    /** 活动默认招商负责人（colonelsettlement_activity.recruiter_user_id）。 */
    UUID findActivityDefaultRecruiterId(String activityId);
}
