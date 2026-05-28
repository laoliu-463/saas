package com.colonel.saas.dto.sample;

/**
 * 寄样筛选选项条目 DTO。
 * <p>
 * 表示寄样筛选下拉列表中的单个选项，用于前端筛选器的选项渲染。
 * 关联业务领域：寄样域（Sample）。
 * </p>
 */
public record SampleFilterOptionItem(
        /** 选项显示标签 */
        String label,
        /** 选项值（提交给后端的标识） */
        String value) {
}
