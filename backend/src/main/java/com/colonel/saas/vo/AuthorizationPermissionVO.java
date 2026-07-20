package com.colonel.saas.vo;

import lombok.Data;

/** Permission catalog item exposed to role administration. */
@Data
public class AuthorizationPermissionVO {
    private String permissionCode;
    private String domainCode;
    private String resourceCode;
    private String actionCode;
}
