package com.colonel.saas.domain.talent.facade.dto;

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
        String ipLocation) {
}
