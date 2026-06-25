package com.colonel.saas.domain.user.port;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.auth.dto.DeptMemberPageRequest;
import com.colonel.saas.vo.SysUserVO;

import java.util.UUID;

/**
 * 组织单元成员分页查询端口。
 */
public interface OrgUnitMemberLookup {

    IPage<SysUserVO> findMembers(UUID orgUnitId, DeptMemberPageRequest request);
}
