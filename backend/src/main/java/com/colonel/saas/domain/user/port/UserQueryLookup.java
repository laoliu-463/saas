package com.colonel.saas.domain.user.port;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.auth.dto.SysUserPageRequest;
import com.colonel.saas.vo.SysUserVO;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 系统用户分页查询和角色 ID 批量读取端口。
 */
public interface UserQueryLookup {

    IPage<SysUserVO> findPage(
            long pageNo,
            long pageSize,
            SysUserPageRequest request,
            UserQueryFilter filter);

    Map<UUID, List<UUID>> findRoleIdsByUserIds(Collection<UUID> userIds);

    record UserQueryFilter(UUID userId, UUID deptId) {

        public static UserQueryFilter none() {
            return new UserQueryFilter(null, null);
        }

        public static UserQueryFilter user(UUID userId) {
            return new UserQueryFilter(userId, null);
        }

        public static UserQueryFilter dept(UUID deptId) {
            return new UserQueryFilter(null, deptId);
        }
    }
}
