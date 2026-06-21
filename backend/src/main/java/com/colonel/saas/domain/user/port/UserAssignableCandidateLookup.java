package com.colonel.saas.domain.user.port;

import com.colonel.saas.vo.SysUserVO;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 可分配负责人候选人查询端口。
 */
public interface UserAssignableCandidateLookup {

    List<AssignableCandidate> findActiveCandidates(String keyword, int limit);

    SysUserVO toVO(AssignableCandidate candidate, Map<UUID, List<UUID>> roleIdsByUserId);

    record AssignableCandidate(
            UUID id,
            String username,
            String realName,
            String phone,
            String email,
            UUID deptId,
            Integer status,
            Boolean forcePasswordChange,
            java.time.LocalDateTime lastLoginAt,
            java.time.LocalDateTime createTime) {
    }
}
