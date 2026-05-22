package com.colonel.saas.dto.talent;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TalentOperateRequest {
    @Size(max = 200, message = "操作原因不能超过 200 个字符")
    private String reason;
}
