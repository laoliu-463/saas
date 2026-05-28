package com.colonel.saas.vo;

import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 菜单树节点展示视图对象。
 * <p>
 * 用于前端左侧导航菜单和权限管理页面的树形结构展示。
 * 通过 {@code children} 字段支持多级菜单嵌套，前端可递归渲染为树形组件。
 * 对应 {@code sys_menu} 表的数据，菜单类型包括目录（M）、菜单（C）、按钮（F）。
 * </p>
 *
 * @see com.colonel.saas.mapper.SysMenuMapper
 */
@Data
public class SysMenuVO {
    /** 菜单唯一标识 */
    private UUID id;
    /** 菜单名称 */
    private String menuName;
    /** 菜单类型：M-目录，C-菜单，F-按钮 */
    private String menuType;
    /** 父菜单 ID，用于构建树形结构 */
    private String parentId;
    /** 路由路径，前端页面跳转地址 */
    private String path;
    /** 前端组件路径，Vue 组件的 import 路径 */
    private String component;
    /** 菜单图标 */
    private String icon;
    /** 排序号，值越小越靠前 */
    private Integer sortOrder;
    /** 权限标识，用于按钮级别的权限校验 */
    private String permissionCode;
    /** 是否可见：1-可见，0-隐藏 */
    private Integer visible;
    /** 菜单状态：1-启用，0-禁用 */
    private Integer status;
    /** 子菜单列表，用于递归渲染树形结构 */
    private List<SysMenuVO> children;
}
