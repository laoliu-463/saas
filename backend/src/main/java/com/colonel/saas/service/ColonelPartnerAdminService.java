package com.colonel.saas.service;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.OptimisticLockSupport;
import com.colonel.saas.dto.colonel.ColonelPartnerContactUpdateRequest;
import com.colonel.saas.entity.ColonelPartner;
import com.colonel.saas.mapper.ColonelPartnerMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 团长主数据管理服务（运营后台）。
 *
 * <p>职责：支持运营人员手动维护团长的联系方式（联系人姓名、电话、微信、备注）。
 * 自动同步流程不会覆盖人工维护的联系方式字段。
 *
 * <p>依赖服务/仓储：
 * <ul>
 *   <li>{@link ColonelPartnerMapper} —— 团长主数据数据访问</li>
 * </ul>
 *
 * <p>事务边界：所有写操作在同一事务内完成，失败时整体回滚。
 */
@Service
public class ColonelPartnerAdminService {

    private final ColonelPartnerMapper colonelPartnerMapper;

    public ColonelPartnerAdminService(ColonelPartnerMapper colonelPartnerMapper) {
        this.colonelPartnerMapper = colonelPartnerMapper;
    }

    /**
     * 更新团长联系方式信息。
     * 仅更新请求中非 null 的字段（部分更新语义），并记录操作时间和操作人。
     * 使用乐观锁防止并发更新冲突。
     *
     * @param id        团长主数据ID
     * @param request   联系方式更新请求，各字段可为 null（表示不更新）
     * @param operatorId 操作人ID
     * @return 更新后的团长主数据对象
     * @throws com.colonel.saas.common.exception.BusinessException 团长不存在时抛出 NOT_FOUND 异常
     * @throws com.colonel.saas.common.exception.OptimisticLockSupport 乐观锁冲突时抛出异常
     */
    @Transactional(rollbackFor = Exception.class)
    public ColonelPartner updateContactInfo(UUID id, ColonelPartnerContactUpdateRequest request, UUID operatorId) {
        ColonelPartner partner = colonelPartnerMapper.selectById(id);
        if (partner == null) {
            throw BusinessException.notFound("团长主数据不存在");
        }
        if (request.contactName() != null) {
            partner.setContactName(trimToNull(request.contactName()));
        }
        if (request.contactPhone() != null) {
            partner.setContactPhone(trimToNull(request.contactPhone()));
        }
        if (request.contactWechat() != null) {
            partner.setContactWechat(trimToNull(request.contactWechat()));
        }
        if (request.contactRemark() != null) {
            partner.setContactRemark(trimToNull(request.contactRemark()));
        }
        partner.setManualContactUpdatedAt(LocalDateTime.now());
        partner.setManualContactUpdatedBy(operatorId == null ? null : operatorId.toString());
        OptimisticLockSupport.requireUpdated(colonelPartnerMapper.updateById(partner));
        return partner;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
