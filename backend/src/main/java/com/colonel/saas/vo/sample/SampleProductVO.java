package com.colonel.saas.vo.sample;

import lombok.Data;

import java.util.UUID;

/**
 * 寄样商品精简 VO（Value Object）。
 * <p>
 * 用于寄样流程中商品选择器或下拉列表的数据展示，仅包含商品的核心标识字段。
 * 相比 {@link SampleVO} 中的商品字段，本 VO 聚焦于商品选择场景的最小数据集。
 * </p>
 * <p>
 * 业务领域：寄样域（Sample Domain）— 商品选择。
 * </p>
 */
@Data
public class SampleProductVO {

    /** 商品在系统内的唯一标识（UUID）。 */
    private UUID id;

    /**
     * 商品外部 ID（抖音电商平台原始商品 ID）。
     * <p>
     * 如抖音商品的 num_iid，用于跨系统关联。
     * </p>
     */
    private String productId;

    /** 商品标题/名称。 */
    private String productName;

    /**
     * 构造方法。
     *
     * @param id         商品系统 UUID
     * @param productId  商品外部 ID
     * @param productName 商品名称
     */
    public SampleProductVO(UUID id, String productId, String productName) {
        this.id = id;
        this.productId = productId;
        this.productName = productName;
    }
}
