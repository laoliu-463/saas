package com.colonel.saas.vo;

import lombok.Data;

import java.util.UUID;

@Data
public class SysRoleVO {
    private UUID id;
    private String roleCode;
    private String roleName;
    private Integer dataScope;
    private Integer status;
    private String remark;
}
