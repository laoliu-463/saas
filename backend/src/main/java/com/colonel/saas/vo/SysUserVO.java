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
    private UUID deptId;
    private Integer status;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createTime;
    private List<UUID> roleIds;
}
