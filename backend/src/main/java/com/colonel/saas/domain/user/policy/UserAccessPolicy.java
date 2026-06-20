package com.colonel.saas.domain.user.policy;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.domain.user.port.UserDepartmentLookup;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * 用户访问权限策略（DDD-USER-MIGRATION-U6）。
 *
 * <p>负责把用户详情、更新、删除、重置密码和角色分配中的 self/group/all
 * 访问控制收口到用户域策略层。</p>
 */
@Component
public class UserAccessPolicy {

    private final UserDepartmentLookup userDepartmentLookup;

    public record AccessibleUser(UUID id, UUID deptId) {
    }

    public UserAccessPolicy(UserDepartmentLookup userDepartmentLookup) {
        this.userDepartmentLookup = userDepartmentLookup;
    }

    /**
     * 校验当前操作者是否有权访问目标用户。
     *
     * @param user          目标用户访问读模型
     * @param currentUserId 当前操作者 ID
     * @param dataScope     数据权限范围
     * @throws BusinessException 数据权限为空或访问超出范围用户时抛出
     */
    public void assertCanAccess(AccessibleUser user, UUID currentUserId, DataScope dataScope) {
        if (dataScope == null) {
            throw BusinessException.forbidden("无法确认数据权限，拒绝访问");
        }
        if (dataScope == DataScope.PERSONAL && !user.id().equals(currentUserId)) {
            throw BusinessException.forbidden("无权访问该用户");
        }
        if (dataScope == DataScope.DEPT) {
            Optional<UUID> currentDeptId = userDepartmentLookup.findDepartmentId(currentUserId);
            if (currentDeptId.isEmpty() || user.deptId() == null
                    || !currentDeptId.get().equals(user.deptId())) {
                throw BusinessException.forbidden("无权访问该部门外用户");
            }
        }
    }
}
