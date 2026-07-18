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
        Long windowSales30d,
        List<String> unsupportedFields) {

    /**
     * 保留既有调用方的十字段构造方式；扩展资料未提供时为 null。
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
                mainCategory, categories, ipLocation, null, null, null, null);
    }

    /**
     * 兼容合作单读取窗口销量的既有调用方。
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
            String ipLocation,
            Long windowSales30d) {
        this(
                id,
                douyinUid,
                douyinNo,
                nickname,
                fansCount,
                status,
                avatarUrl,
                mainCategory,
                categories,
                ipLocation,
                null,
                null,
                windowSales30d,
                null);
    }

    /**
     * 兼容既有达人资料调用方；窗口销量未提供时为 null。
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
            String ipLocation,
            String talentLevel,
            Long sales30d,
            List<String> unsupportedFields) {
        this(id, douyinUid, douyinNo, nickname, fansCount, status, avatarUrl,
                mainCategory, categories, ipLocation, talentLevel, sales30d, null, unsupportedFields);
    }
}
