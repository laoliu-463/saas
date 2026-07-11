package com.colonel.saas.domain.colonel.query;

import java.util.List;

/**
 * 团长主数据分页查询请求 DTO（DDD 域内版本，DDD-COLONEL-002 Wave 1.2 补全）。
 *
 * <p>对应 {@code ColonelPartnerMasterDataApplicationService} 的 findPage 输入。
 * 字段与 {@code service.ColonelPartnerMasterDataService.list} 入参一致，
 * 域内 DTO 版本以保持 colonel 域 DDD 边界完整性。</p>
 */
public record ColonelPartnerListQuery(
        /** 关键字（团长名称模糊匹配） */
        String keyword,
        /** 来源（精确匹配） */
        String source,
        /** 是否有联系方式（true / false / null=不限） */
        Boolean hasContact,
        /** 页码（从 1 开始） */
        long page,
        /** 每页条数 */
        long size) {

    /**
     * 空查询条件（返回所有，按默认排序）。
     */
    public static ColonelPartnerListQuery empty() {
        return new ColonelPartnerListQuery(null, null, null, 1, 20);
    }

    /**
     * 关键字查询快捷构造。
     */
    public static ColonelPartnerListQuery ofKeyword(String keyword) {
        return new ColonelPartnerListQuery(keyword, null, null, 1, 20);
    }

    /**
     * 批量关键字提取（保留以支持未来接口扩展）。
     */
    public List<String> getKeywords() {
        return keyword == null || keyword.isBlank() ? List.of() : List.of(keyword.trim());
    }
}
