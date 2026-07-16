package com.colonel.saas.domain.talent.facade.dto;

import java.util.List;
import java.util.UUID;

/**
 * 达人域只读 DTO（DDD-TALENT-001）：跨域查询达人主数据时的字段集。
 */
public record TalentReadDTO(
        UUID id,
        String douyinUid,
        String douyinNo,
        String nickname,
        Long fansCount,
        Integer status,
        String avatarUrl,
        String mainCategory,
        String categories,
        String ipLocation,
        String talentLevel,
        Long sales30d,
        List<String> unsupportedFields) {

    /**
     * 保留跨域调用方原有的基础字段构造方式，新增资料字段按未同步处理。
     */
    public TalentReadDTO(
            UUID id,
            String douyinUid,
            String douyinNo,
            String nickname,
            Long fansCount,
            Integer status,
            String avatarUrl,
            String mainCategory,
            String categories,
            String ipLocation) {
        this(id, douyinUid, douyinNo, nickname, fansCount, status, avatarUrl,
                mainCategory, categories, ipLocation, null, null, null);
    }
}
