package com.colonel.saas.vo.talent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 投诉创建结果或领导详情。风险批量接口不使用该对象。 */
public record TalentComplaintVO(
        UUID id,
        UUID sampleRequestId,
        UUID talentId,
        UUID productId,
        UUID reporterUserId,
        String reasonCode,
        String content,
        String status,
        List<AttachmentVO> attachments,
        LocalDateTime createTime) {

    public record AttachmentVO(
            UUID id,
            String originalName,
            String contentType,
            long fileSize,
            String sha256) {
    }
}
