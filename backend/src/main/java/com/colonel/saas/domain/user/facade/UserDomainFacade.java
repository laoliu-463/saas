package com.colonel.saas.domain.user.facade;

import com.colonel.saas.domain.user.facade.dto.DepartmentOption;
import com.colonel.saas.domain.user.facade.dto.UserOwnershipReference;
import com.colonel.saas.dto.user.UserDataScopeResponse;
import com.colonel.saas.dto.user.UserOptionResponse;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 用户域只读门面（DDD-USER-001）。
 * <p>
 * 其他业务域应通过本接口获取数据范围与人员主数据，禁止新增跨域 {@code SysUserMapper} 注入。
 * 第一版内部委派既有 {@code UserDomainService} / {@code UserMasterDataService}，不改变线上行为。
 * </p>
 */
public interface UserDomainFacade {

    /**
     * 解析指定用户的数据范围（self / group / all）及可见用户 ID 列表。
     */
    UserDataScopeResponse resolveDataScope(UUID userId);

    /**
     * 渠道人员下拉（CHANNEL_LEADER / CHANNEL_STAFF）。
     */
    List<UserOptionResponse> listChannels(String keyword);

    /**
     * 招商人员下拉（BIZ_LEADER / BIZ_STAFF）。
     */
    List<UserOptionResponse> listRecruiters(String keyword);

    /**
     * 启用状态的部门/组织单元列表。
     */
    List<DepartmentOption> listDepartments();

    /**
     * 指定组织单元下的成员下拉。
     *
     * @param groupId       目标部门 ID（非 admin 时回退为当前用户部门）
     * @param currentUserId 当前操作用户，用于权限与部门回退
     */
    List<UserOptionResponse> listGroupMembers(UUID groupId, UUID currentUserId);

    /**
     * 检查用户是否拥有指定资源操作权限。
     */
    boolean hasPermission(UUID userId, String resource, String action);

    /**
     * 判断给定角色集合是否包含任一目标角色。
     * 业务域只传入当前用户角色事实，不复制角色编码规范化规则。
     */
    boolean hasAnyRole(Object roleCodes, String... expectedRoles);

    /**
     * 规范化角色编码集合，供跨域入口复用用户域统一的 trim/lowercase/去重规则。
     */
    List<String> normalizeRoleCodes(Object roleCodes);

    /**
     * 按用户 ID 查询真实姓名，不存在时返回 null（DDD-USER-002）。
     */
    String getUserName(UUID userId);

    /**
     * 按用户 ID 查询登录账号，不存在时返回 null。
     */
    String getUsername(UUID userId);

    /**
     * 按用户 ID 查询用户基础信息（DDD-USER-002）。
     */
    UserOptionResponse getUserById(UUID userId);

    /**
     * 批量加载用户基础信息（DDD-USER-002）。
     */
    List<UserOptionResponse> getUsersByIds(Collection<UUID> ids);

    /**
     * 批量加载用户真实姓名，返回 userId → realName 映射（DDD-USER-002）。
     * 自动过滤 null 和重复 ID。
     */
    Map<UUID, String> loadUserNamesByIds(Collection<UUID> ids);

    /**
     * 批量加载用户展示名称，返回 userId → displayName 映射。
     * 展示名称优先使用 realName，再回退到 username。
     */
    Map<UUID, String> loadUserDisplayNamesByIds(Collection<UUID> ids);

    /**
     * 批量加载用户展示标签，返回 userId → displayLabel 映射。
     * 展示标签优先使用 "realName (username)"，再回退到 realName 或 username。
     */
    Map<UUID, String> loadUserDisplayLabelsByIds(Collection<UUID> ids);

    /**
     * 批量加载用户渠道编码，返回 userId -> channelCode 映射。
     * 用于推广链接归因参数构造，避免业务域读取完整用户 DTO。
     */
    Map<UUID, String> loadUserChannelCodesByIds(Collection<UUID> ids);

    /**
     * 批量加载用户归属引用，返回 userId -> ownership reference 映射。
     * 用于跨业务域归属覆盖时确认目标用户存在并读取其主组织单元。
     */
    Map<UUID, UserOwnershipReference> loadUserOwnershipReferencesByIds(Collection<UUID> ids);

    /**
     * 按类型过滤加载部门/组织单元列表。
     */
    List<DepartmentOption> listDepartments(Collection<String> deptTypes);
}
