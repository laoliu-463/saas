package com.colonel.saas.domain.user.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.colonel.saas.domain.user.port.UserAssignableCandidateLookup;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.vo.SysUserVO;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 通过现有 sys_user 查询可分配负责人候选人的过渡适配器。
 */
@Component
public class SysUserAssignableCandidateLookupAdapter implements UserAssignableCandidateLookup {

    private final SysUserMapper sysUserMapper;

    public SysUserAssignableCandidateLookupAdapter(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public List<AssignableCandidate> findActiveCandidates(String keyword, int limit) {
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0)
                .eq("status", 1)
                .orderByAsc("real_name")
                .orderByAsc("username")
                .last("limit " + limit);
        if (keyword != null && !keyword.trim().isEmpty()) {
            String safeKeyword = keyword.trim();
            wrapper.and(query -> query.like("username", safeKeyword).or().like("real_name", safeKeyword));
        }
        return sysUserMapper.selectList(wrapper).stream()
                .map(this::toCandidate)
                .toList();
    }

    @Override
    public SysUserVO toVO(AssignableCandidate candidate, Map<UUID, List<UUID>> roleIdsByUserId) {
        SysUserVO vo = new SysUserVO();
        vo.setId(candidate.id());
        vo.setUsername(candidate.username());
        vo.setRealName(candidate.realName());
        vo.setPhone(candidate.phone());
        vo.setEmail(candidate.email());
        vo.setDeptId(candidate.deptId());
        vo.setStatus(candidate.status());
        vo.setForcePasswordChange(candidate.forcePasswordChange());
        vo.setLastLoginAt(candidate.lastLoginAt());
        vo.setCreateTime(candidate.createTime());
        vo.setRoleIds(roleIdsByUserId.getOrDefault(candidate.id(), Collections.emptyList()));
        return vo;
    }

    private AssignableCandidate toCandidate(SysUser user) {
        return new AssignableCandidate(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getPhone(),
                user.getEmail(),
                user.getDeptId(),
                user.getStatus(),
                user.getForcePasswordChange(),
                user.getLastLoginAt(),
                user.getCreateTime());
    }
}
