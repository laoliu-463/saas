package com.colonel.saas.mapper.projection;

import lombok.Data;

import java.util.UUID;

@Data
public class AuthorizationSnapshotRow {

    private UUID userId;
    private UUID deptId;
    private Long authzVersion;
    private UUID roleId;
    private String permissionCode;
    private String domainCode;
    private Boolean dataScopeRequired;
    private String scopeCode;
}
