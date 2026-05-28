package com.colonel.saas.dto.product;

import java.util.List;

/**
 * 商品筛选选项集合 DTO。
 * <p>
 * 返回商品列表页所有筛选器的选项数据，按类目分组。
 * 关联业务领域：商品域（Product）。
 * </p>
 */
public record ProductFilterOptionsDTO(
        /** 商品类目筛选选项列表 */
        List<ProductFilterOptionItem> categories) {

    /**
     * 创建空的筛选选项实例。
     *
     * @return 不包含任何选项的空 {@link ProductFilterOptionsDTO}
     */
    public static ProductFilterOptionsDTO empty() {
        return new ProductFilterOptionsDTO(List.of());
    }
}
