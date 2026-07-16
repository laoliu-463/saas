package com.colonel.saas.domain.talent.facade.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 跨域可见的达人投诉风险摘要，不包含投诉正文或举报人。
 */
public record TalentComplaintRiskDTO(
        UUID talentId,
        long complaintCount,
        LocalDateTime lastComplaintAt) {
}
