package com.colonel.saas.vo;

import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 菜单树节点 VO
 */
@Data
public class SysMenuVO {
    private UUID id;
    private String menuName;
    private String menuType;
    private String parentId;
    private String path;
    private String component;
    private String icon;
    private Integer sortOrder;
    private String permissionCode;
    private Integer visible;
    private Integer status;
    private List<SysMenuVO> children;
}
