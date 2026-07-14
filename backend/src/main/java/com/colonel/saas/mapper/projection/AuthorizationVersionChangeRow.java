package com.colonel.saas.mapper.projection;

import lombok.Data;

import java.util.UUID;

@Data
public class AuthorizationVersionChangeRow {

    private UUID userId;
    private Long previousVersion;
    private Long currentVersion;
}
