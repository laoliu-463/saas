package com.colonel.saas.domain.user.infrastructure;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.auth.dto.DeptMemberPageRequest;
import com.colonel.saas.auth.service.SysUserService;
import com.colonel.saas.domain.user.port.OrgUnitMemberLookup;
import com.colonel.saas.vo.SysUserVO;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 通过现有 SysUserService 查询组织单元成员的过渡适配器。
 */
@Component
public class SysOrgUnitMemberLookupAdapter implements OrgUnitMemberLookup {

    private final SysUserService sysUserService;

    public SysOrgUnitMemberLookupAdapter(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    @Override
    public IPage<SysUserVO> findMembers(UUID orgUnitId, DeptMemberPageRequest request) {
        return sysUserService.findDeptMembers(orgUnitId, request);
    }
}
