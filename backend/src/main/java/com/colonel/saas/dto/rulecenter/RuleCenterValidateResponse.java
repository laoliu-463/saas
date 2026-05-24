package com.colonel.saas.dto.rulecenter;

import java.util.List;

public record RuleCenterValidateResponse(
        boolean valid,
        List<String> errors,
        List<String> warnings) {
}
