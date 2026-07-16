package com.colonel.saas.vo.talent;

import java.time.LocalDateTime;
import java.util.UUID;

public record TalentComplaintReminderVO(
        UUID id,
        UUID complaintId,
        UUID talentId,
        String reasonCode,
        boolean read,
        LocalDateTime createTime,
        LocalDateTime readAt) {
}
