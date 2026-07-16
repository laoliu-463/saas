package com.colonel.saas.dto.talent;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record TalentComplaintRiskRequest(
        @NotEmpty(message = "talentIds 不能为空")
        @Size(max = 100, message = "talentIds 最多 100 个")
        List<UUID> talentIds) {
}
