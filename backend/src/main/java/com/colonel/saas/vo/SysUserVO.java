package com.colonel.saas.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class SysUserVO {
    private UUID id;
    private String username;
    private String realName;
    private String phone;
    private String email;
    /** 持久化组织节点 ID（业务组或部门），用于 data_scope 本组过滤 */
    private UUID deptId;
    private UUID parentDeptId;
    private String parentDeptName;
    private UUID groupId;
    private String groupName;
    private String groupType;
    private UUID roleId;
    private String roleCode;
    private String roleName;
    private Integer status;
    private Boolean forcePasswordChange;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createTime;
    private List<UUID> roleIds;
}
