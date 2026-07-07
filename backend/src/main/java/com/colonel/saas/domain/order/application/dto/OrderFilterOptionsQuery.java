package com.colonel.saas.domain.order.application.dto;

import com.colonel.saas.common.enums.DataScope;
import org.springframework.util.StringUtils;

import java.util.UUID;

public record OrderFilterOptionsQuery(String keyword, UUID userId, UUID deptId, DataScope dataScope) {

    public boolean hasKeyword() {
        return StringUtils.hasText(keyword);
    }
}
