package com.colonel.saas.dto.talent;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 达人操作请求 DTO。
 * <p>
 * 用于对达人执行拉黑、解封等操作时提交操作原因，便于审计追溯。
 * 关联业务领域：达人域（Talent）。
 * </p>
 */
@Data
public class TalentOperateRequest {
    /** 操作原因说明，最大 200 字符 */
    @Size(max = 200, message = "操作原因不能超过 200 个字符")
    private String reason;
}
