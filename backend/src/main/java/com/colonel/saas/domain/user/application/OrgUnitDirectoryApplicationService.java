package com.colonel.saas.domain.user.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.auth.dto.DeptMemberPageRequest;
import com.colonel.saas.domain.user.port.OrgUnitDirectoryLookup;
import com.colonel.saas.domain.user.port.OrgUnitDirectoryLookup.OrgUnitEntry;
import com.colonel.saas.domain.user.port.OrgUnitMemberLookup;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.DeptType;
import com.colonel.saas.vo.DeptStatsVO;
import com.colonel.saas.vo.SysDeptVO;
import com.colonel.saas.vo.SysUserVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 组织单元目录应用服务。
 */
@Service
public class OrgUnitDirectoryApplicationService {

    private final OrgUnitDirectoryLookup orgUnitDirectoryLookup;
    private final OrgUnitMemberLookup orgUnitMemberLookup;

    public OrgUnitDirectoryApplicationService(
            OrgUnitDirectoryLookup orgUnitDirectoryLookup,
            OrgUnitMemberLookup orgUnitMemberLookup) {
        this.orgUnitDirectoryLookup = orgUnitDirectoryLookup;
        this.orgUnitMemberLookup = orgUnitMemberLookup;
    }

    public List<SysDeptVO> findAll() {
        return orgUnitDirectoryLookup.listActive().stream()
                .map(this::toVO)
                .toList();
    }

    public List<SysDeptVO> findTree() {
        Map<UUID, SysDeptVO> index = new LinkedHashMap<>();
        for (OrgUnitEntry entry : orgUnitDirectoryLookup.listActive()) {
            index.put(entry.id(), toVO(entry));
        }
        List<SysDeptVO> roots = new ArrayList<>();
        for (SysDeptVO node : index.values()) {
            if (node.getParentId() != null && index.containsKey(node.getParentId())) {
                index.get(node.getParentId()).getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }

    public SysDeptVO getById(UUID id) {
        return orgUnitDirectoryLookup.findActiveById(id)
                .map(this::toVO)
                .orElseThrow(() -> BusinessException.notFound("部门不存在"));
    }

    public List<SysDeptVO> findGroupsByParent(UUID parentId, String deptType) {
        getById(parentId);
        return orgUnitDirectoryLookup.findChildren(parentId).stream()
                .filter(entry -> !StringUtils.hasText(deptType)
                        || DeptType.normalize(deptType).equals(DeptType.normalize(entry.deptType())))
                .filter(entry -> DeptType.isGroup(entry.deptType()))
                .map(this::toVO)
                .toList();
    }

    public DeptStatsVO getStats(UUID deptId) {
        getById(deptId);
        DeptStatsVO stats = new DeptStatsVO();
        stats.setDeptId(deptId);
        stats.setMemberCount(orgUnitDirectoryLookup.countMembersUnderOrgUnit(deptId));
        stats.setRecruiterGroupCount(orgUnitDirectoryLookup.countChildGroupsByType(deptId, DeptType.RECRUITER_GROUP));
        stats.setChannelGroupCount(orgUnitDirectoryLookup.countChildGroupsByType(deptId, DeptType.CHANNEL_GROUP));
        return stats;
    }

    public IPage<SysUserVO> findMembers(UUID deptId, DeptMemberPageRequest request) {
        getById(deptId);
        return orgUnitMemberLookup.findMembers(deptId, request);
    }

    private SysDeptVO toVO(OrgUnitEntry entry) {
        SysDeptVO vo = new SysDeptVO();
        vo.setId(entry.id());
        vo.setParentId(entry.parentId());
        vo.setDeptCode(entry.deptCode());
        vo.setDeptName(entry.deptName());
        vo.setDeptType(entry.deptType());
        vo.setLeaderUserId(entry.leaderUserId());
        vo.setLeader(entry.leader());
        vo.setPhone(entry.phone());
        vo.setEmail(entry.email());
        vo.setSortOrder(entry.sortOrder());
        vo.setStatus(entry.status());
        vo.setRemark(entry.remark());
        return vo;
    }
}
