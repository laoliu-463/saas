package com.colonel.saas.service;

import com.colonel.saas.auth.dto.SysDeptCreateRequest;
import com.colonel.saas.auth.dto.SysDeptUpdateRequest;
import com.colonel.saas.domain.user.application.SysDeptApplicationService;
import com.colonel.saas.domain.user.port.OrgDepartmentRepository.DepartmentRecord;
import com.colonel.saas.entity.SysDept;
import com.colonel.saas.vo.SysDeptVO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 部门与组别服务（Legacy 委派壳，DDD-USER-MIGRATION-006，Issue #15）。
 *
 * <p>本服务已迁移到 {@code domain.user.application.SysDeptApplicationService}，
 * 现作为薄壳 facade 保留向后兼容的 public 方法签名。
 * 所有业务逻辑委托给 ApplicationService。</p>
 *
 * <p><b>迁移路径</b>：
 * <ul>
 *   <li>listActive / listByDeptType / listTree / getById → SysDeptApplicationService</li>
 *   <li>create / update / delete → SysDeptApplicationService</li>
 * </ul>
 *
 * <p><b>保留 API</b>：
 * <ul>
 *   <li>7 个 public 方法签名保留（外部调用方零改动）</li>
 *   <li>Bean 名 \`legacySysDeptService\` 保留（防止其他 @Qualifier 引用断裂）</li>
 * </ul>
 *
 * <p>所属业务领域：用户域 / 组织架构（Legacy）</p>
 */
@Service("legacySysDeptService")
public class SysDeptService {

    private final SysDeptApplicationService applicationService;

    public SysDeptService(SysDeptApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    public List<SysDept> listActive() {
        return applicationService.listActive().stream().map(this::toEntity).toList();
    }

    public List<SysDept> listByDeptType(String deptType) {
        return applicationService.listByDeptType(deptType).stream().map(this::toEntity).toList();
    }

    public List<SysDeptVO> listTree() {
        return applicationService.listTree();
    }

    public SysDeptVO getById(UUID id) {
        return applicationService.getById(id);
    }

    public SysDeptVO create(SysDeptCreateRequest request, UUID currentUserId) {
        return applicationService.create(request, currentUserId);
    }

    public SysDeptVO update(UUID id, SysDeptUpdateRequest request, UUID currentUserId) {
        return applicationService.update(id, request, currentUserId);
    }

    public void delete(UUID id, UUID currentUserId) {
        applicationService.delete(id, currentUserId);
    }

    private SysDept toEntity(DepartmentRecord record) {
        SysDept dept = new SysDept();
        dept.setId(record.id());
        dept.setParentId(record.parentId());
        dept.setDeptCode(record.deptCode());
        dept.setDeptName(record.deptName());
        dept.setDeptType(record.deptType());
        dept.setLeaderUserId(record.leaderUserId());
        dept.setLeader(record.leader());
        dept.setPhone(record.phone());
        dept.setEmail(record.email());
        dept.setSortOrder(record.sortOrder());
        dept.setStatus(record.status());
        dept.setRemark(record.remark());
        dept.setDeleted(record.deleted());
        return dept;
    }
}
