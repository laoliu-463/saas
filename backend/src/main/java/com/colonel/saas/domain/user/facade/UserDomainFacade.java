package com.colonel.saas.domain.user.facade;

import com.colonel.saas.domain.user.facade.dto.DepartmentOption;
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
     * 按用户 ID 查询真实姓名，不存在时返回 null（DDD-USER-002）。
     */
    String getUserName(UUID userId);

    /**
     * 批量加载用户真实姓名，返回 userId → realName 映射（DDD-USER-002）。
     * 自动过滤 null 和重复 ID。
     */
    Map<UUID, String> loadUserNamesByIds(Collection<UUID> ids);
}
