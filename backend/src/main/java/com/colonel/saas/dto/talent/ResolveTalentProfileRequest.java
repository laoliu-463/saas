package com.colonel.saas.dto.talent;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class ResolveTalentProfileRequest {

    @NotBlank(message = "input 不能为空")
    private String input;

    private Boolean forceRefresh;

    private Boolean manualFill;

    private Map<String, Object> manualPayload;
}
