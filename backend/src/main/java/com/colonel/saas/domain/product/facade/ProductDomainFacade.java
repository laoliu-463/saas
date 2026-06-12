package com.colonel.saas.domain.product.facade;

import com.colonel.saas.domain.product.facade.dto.ProductReadDTO;
import com.colonel.saas.domain.product.facade.dto.ProductSnapshotReadDTO;

import java.util.Collection;
import java.util.Map;
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

    /**
     * 批量加载商品名称，返回 productId → name 映射。
     * 自动过滤 null 和重复 ID；缺失记录不包含在结果中。
     */
    Map<UUID, String> loadProductNamesByIds(Collection<UUID> ids);
}
