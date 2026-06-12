package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.user.UserOptionResponse;
import com.colonel.saas.entity.SysRole;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.entity.SysUserRole;
import com.colonel.saas.mapper.SysRoleMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.mapper.SysUserRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 用户主数据查询服务。
 * <p>
 * 按角色维度提供用户下拉选项查询，用于渠道人员选择、招募人员选择和组成员列表。
 * 支持关键词模糊搜索、结果数量限制和管理员跨部门查看能力。
 * </p>
 *
 * <ul>
 *     <li>查询渠道人员选项列表（{@link #listChannels}）</li>
 *     <li>查询招募人员选项列表（{@link #listRecruiters}）</li>
 *     <li>查询组成员选项列表（{@link #listGroupMembers}）</li>
 * </ul>
 *
 * <p><b>业务域：</b>用户域 — 主数据查询</p>
 * <p><b>协作关系：</b></p>
 * <ul>
 *     <li>{@link SysUserMapper} — 用户数据访问</li>
 *     <li>{@link SysRoleMapper} — 角色数据访问</li>
 *     <li>{@link SysUserRoleMapper} — 用户角色关联数据访问</li>
 * </ul>
 */
@Service
public class UserMasterDataService {

    /** 默认返回条数上限 */
    private static final int DEFAULT_LIMIT = 50;
    /** 最大返回条数上限 */
    private static final int MAX_LIMIT = 100;
    /** 渠道相关角色编码列表 */
    private static final List<String> CHANNEL_ROLE_CODES = List.of(RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF);
    /** 招募相关角色编码列表 */
    private static final List<String> RECRUITER_ROLE_CODES = List.of(RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF);

    /** 用户数据访问 Mapper */
    private final SysUserMapper sysUserMapper;
    /** 角色数据访问 Mapper */
    private final SysRoleMapper sysRoleMapper;
    /** 用户角色关联数据访问 Mapper */
    private final SysUserRoleMapper sysUserRoleMapper;

    public UserMasterDataService(
            SysUserMapper sysUserMapper,
            SysRoleMapper sysRoleMapper,
            SysUserRoleMapper sysUserRoleMapper) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
    }

    /**
     * 查询渠道人员选项列表。
     * <p>根据渠道相关角色编码（CHANNEL_LEADER、CHANNEL_STAFF）查询用户列表。</p>
     *
     * @param keyword 搜索关键词（模糊匹配用户名/姓名，可为 null）
     * @param limit   最大返回条数（可为 null，使用默认值）
     * @return 渠道人员选项列表
     */
    public List<UserOptionResponse> listChannels(String keyword, Integer limit) {
        return listByRoleCodes(CHANNEL_ROLE_CODES, keyword, limit);
    }

    /**
     * 查询招募人员选项列表。
     * <p>根据招募相关角色编码（BIZ_LEADER、BIZ_STAFF）查询用户列表。</p>
     *
     * @param keyword 搜索关键词（模糊匹配用户名/姓名，可为 null）
     * @param limit   最大返回条数（可为 null，使用默认值）
     * @return 招募人员选项列表
     */
    public List<UserOptionResponse> listRecruiters(String keyword, Integer limit) {
        return listByRoleCodes(RECRUITER_ROLE_CODES, keyword, limit);
    }

    /**
     * 查询组成员选项列表。
     * <p>处理流程：</p>
     * <ol>
     *     <li>判断当前用户是否有跨部门查看权限（仅 ADMIN 可查看其他部门）</li>
     *     <li>解析目标部门 ID，无权限时回退到当前用户所属部门</li>
     *     <li>查询该部门下未删除且启用的用户列表</li>
     *     <li>转换为选项响应并应用关键词过滤和数量限制</li>
     * </ol>
     *
     * @param requestedDeptId   请求的目标部门 ID（可为 null）
     * @param currentDeptId     当前用户所属部门 ID
     * @param currentRoleCodes  当前用户的角色编码列表
     * @param keyword           搜索关键词（可为 null）
     * @param limit             最大返回条数（可为 null，使用默认值）
     * @return 组成员选项列表
     */
    public List<UserOptionResponse> listGroupMembers(
            UUID requestedDeptId,
            UUID currentDeptId,
            List<String> currentRoleCodes,
            String keyword,
            Integer limit) {
        // 第一步：解析目标部门 ID，仅 ADMIN 可查看其他部门
        UUID resolvedDeptId = canViewRequestedDept(currentRoleCodes) && requestedDeptId != null
                ? requestedDeptId
                : currentDeptId;
        if (resolvedDeptId == null) {
            return Collections.emptyList();
        }
        // 第二步：查询该部门下活跃用户
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0)
                .eq("status", 1)
                .eq("dept_id", resolvedDeptId);
        List<SysUser> users = sysUserMapper.selectList(wrapper);
        return toOptions(users, null, keyword, limit);
    }

    /**
     * 按角色编码查询用户选项列表。
     * <p>处理流程：</p>
     * <ol>
     *     <li>根据角色编码查找启用的角色实体</li>
     *     <li>通过用户角色关联表获取拥有这些角色的用户 ID 集合</li>
     *     <li>批量查询用户实体</li>
     *     <li>转换为选项响应并应用角色过滤、关键词搜索和数量限制</li>
     * </ol>
     *
     * @param roleCodes 角色编码集合
     * @param keyword   搜索关键词
     * @param limit     最大返回条数
     * @return 用户选项列表
     */
    private List<UserOptionResponse> listByRoleCodes(Collection<String> roleCodes, String keyword, Integer limit) {
        // 第一步：查找启用状态的角色
        List<SysRole> roles = roleCodes.stream()
                .map(sysRoleMapper::findByRoleCode)
                .flatMap(optional -> optional.stream())
                .filter(role -> role.getStatus() == null || role.getStatus() == 1)
                .toList();
        if (roles.isEmpty()) {
            return Collections.emptyList();
        }
        // 第二步：获取拥有这些角色的用户 ID
        Set<UUID> roleIds = roles.stream()
                .map(SysRole::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<UUID> userIds = new LinkedHashSet<>();
        for (UUID roleId : roleIds) {
            sysUserRoleMapper.findByRoleId(roleId).stream()
                    .map(SysUserRole::getUserId)
                    .filter(Objects::nonNull)
                    .forEach(userIds::add);
        }
        if (userIds.isEmpty()) {
            return Collections.emptyList();
        }
        // 第三步：批量查询用户并转换为选项
        List<SysUser> users = sysUserMapper.selectBatchIds(userIds);
        return toOptions(users, roleCodes, keyword, limit);
    }

    /**
     * 将用户实体列表转换为选项响应列表。
     * <p>过滤未启用用户，映射为 {@link UserOptionResponse}，按指定角色过滤，
     * 关键词搜索，按姓名/用户名排序后截取指定条数。</p>
     *
     * @param users           用户实体列表
     * @param allowedRoleCodes 允许的角色编码（null 时不做角色过滤）
     * @param keyword         搜索关键词
     * @param limit           最大返回条数
     * @return 过滤排序后的用户选项列表
     */
    private List<UserOptionResponse> toOptions(
            List<SysUser> users,
            Collection<String> allowedRoleCodes,
            String keyword,
            Integer limit) {
        if (users == null || users.isEmpty()) {
            return Collections.emptyList();
        }
        Map<UUID, List<String>> roleCodesByUser = loadRoleCodes(users);
        String normalizedKeyword = normalize(keyword);
        int safeLimit = safeLimit(limit);
        Set<String> allowed = allowedRoleCodes == null
                ? Collections.emptySet()
                : allowedRoleCodes.stream().collect(Collectors.toSet());
        return users.stream()
                .filter(this::isActive)
                .map(user -> new UserOptionResponse(
                        user.getId(),
                        user.getUsername(),
                        user.getRealName(),
                        user.getDeptId(),
                        roleCodesByUser.getOrDefault(user.getId(), Collections.emptyList()),
                        user.getChannelCode()
                ))
                .filter(option -> allowed.isEmpty() || option.roleCodes().stream().anyMatch(allowed::contains))
                .filter(option -> matchesKeyword(option, normalizedKeyword))
                .sorted(Comparator
                        .comparing((UserOptionResponse option) -> firstNonBlank(option.realName(), option.username()))
                        .thenComparing(UserOptionResponse::username, Comparator.nullsLast(String::compareTo)))
                .limit(safeLimit)
                .toList();
    }

    /**
     * 批量加载用户的角色编码映射。
     * <p>通过用户角色关联表查询用户对应的角色 ID，再批量查询角色实体获取角色编码，
     * 最终返回 userId -> roleCodes 映射。</p>
     *
     * @param users 用户实体列表
     * @return 用户 ID 到角色编码列表的映射
     */
    private Map<UUID, List<String>> loadRoleCodes(List<SysUser> users) {
        List<UUID> userIds = users.stream()
                .map(SysUser::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<SysUserRole> relations = sysUserRoleMapper.findByUserIds(userIds);
        if (relations == null || relations.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<UUID> roleIds = relations.stream()
                .map(SysUserRole::getRoleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, SysRole> roleMap = roleIds.isEmpty()
                ? Collections.emptyMap()
                : sysRoleMapper.selectBatchIds(roleIds).stream()
                .collect(Collectors.toMap(SysRole::getId, role -> role, (left, right) -> left));
        Map<UUID, List<String>> result = relations.stream()
                .filter(relation -> relation.getUserId() != null && relation.getRoleId() != null)
                .collect(Collectors.groupingBy(
                        SysUserRole::getUserId,
                        Collectors.mapping(relation -> {
                            SysRole role = roleMap.get(relation.getRoleId());
                            return role == null ? null : role.getRoleCode();
                        }, Collectors.toList())
                ));
        result.replaceAll((userId, codes) -> codes.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList());
        return result;
    }

    /**
     * 判断用户是否处于启用状态（未删除且状态正常）。
     *
     * @param user 用户实体
     * @return true 表示启用
     */
    private boolean isActive(SysUser user) {
        return user != null
                && user.getId() != null
                && (user.getDeleted() == null || user.getDeleted() == 0)
                && (user.getStatus() == null || user.getStatus() == 1);
    }

    /**
     * 判断用户选项是否匹配关键词（用户名或姓名模糊匹配）。
     *
     * @param option  用户选项
     * @param keyword 标准化后的关键词
     * @return true 表示匹配
     */
    private boolean matchesKeyword(UserOptionResponse option, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        return normalize(option.username()).contains(keyword)
                || normalize(option.realName()).contains(keyword);
    }

    /**
     * 判断当前用户是否有权限查看其他部门的成员。
     * <p>仅 ADMIN 角色允许跨部门查看。</p>
     *
     * @param roleCodes 当前用户角色编码列表
     * @return true 表示允许跨部门查看
     */
    private boolean canViewRequestedDept(List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return false;
        }
        return roleCodes.contains(RoleCodes.ADMIN);
    }

    /**
     * 安全化分页限制值，在默认值和最大值之间取值。
     *
     * @param limit 请求的限制值
     * @return 安全化的限制值
     */
    private int safeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    /**
     * 返回两个字符串中第一个非空白的值。
     *
     * @param first  优先返回的字符串
     * @param second 备选字符串
     * @return 第一个非空白值，均为空时返回空字符串
     */
    private String firstNonBlank(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first;
        }
        return second == null ? "" : second;
    }

    /**
     * 标准化字符串：trim 后转小写，null 返回空字符串。
     *
     * @param value 原始字符串
     * @return 标准化后的字符串
     */
    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
