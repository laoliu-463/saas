package com.colonel.saas.dto.product;

/**
 * 商品筛选选项条目 DTO。
 * <p>
 * 表示商品筛选下拉列表中的单个选项，用于前端筛选器的选项渲染。
 * 关联业务领域：商品域（Product）。
 * </p>
 */
public record ProductFilterOptionItem(
        /** 选项显示标签 */
        String label,
        /** 选项值（提交给后端的标识） */
        String value,
        /** 该选项对应的记录数 */
        Long count) {
}
