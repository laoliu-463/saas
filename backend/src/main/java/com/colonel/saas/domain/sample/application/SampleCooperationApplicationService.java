package com.colonel.saas.domain.sample.application;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.enums.SampleStatus;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.OptimisticLockSupport;
import com.colonel.saas.domain.sample.policy.SampleCooperationActionPolicy;
import com.colonel.saas.domain.sample.policy.SampleRemarkPolicy;
import com.colonel.saas.domain.talent.facade.TalentDomainFacade;
import com.colonel.saas.domain.talent.facade.dto.TalentClaimAddressDTO;
import com.colonel.saas.domain.talent.facade.dto.TalentReadDTO;
import com.colonel.saas.dto.sample.SampleCooperationUpdateRequest;
import com.colonel.saas.dto.sample.SamplePrivateNoteRequest;
import com.colonel.saas.entity.SamplePrivateNote;
import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.mapper.SamplePrivateNoteMapper;
import com.colonel.saas.mapper.SampleRequestMapper;
import com.colonel.saas.vo.sample.SampleEditContextVO;
import com.colonel.saas.vo.sample.SamplePrivateNoteVO;
import com.colonel.saas.vo.sample.SampleVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 合作台编辑与当前用户私有备注用例。
 */
@Service
public class SampleCooperationApplicationService {

    private final SampleQueryApplicationService sampleQueryApplicationService;
    private final SampleRequestMapper sampleRequestMapper;
    private final SamplePrivateNoteMapper samplePrivateNoteMapper;
    private final TalentDomainFacade talentDomainFacade;
    private final SampleCooperationActionPolicy actionPolicy;
    private final SampleRemarkPolicy remarkPolicy;

    public SampleCooperationApplicationService(
            SampleQueryApplicationService sampleQueryApplicationService,
            SampleRequestMapper sampleRequestMapper,
            SamplePrivateNoteMapper samplePrivateNoteMapper,
            TalentDomainFacade talentDomainFacade,
            SampleCooperationActionPolicy actionPolicy,
            SampleRemarkPolicy remarkPolicy) {
        this.sampleQueryApplicationService = sampleQueryApplicationService;
        this.sampleRequestMapper = sampleRequestMapper;
        this.samplePrivateNoteMapper = samplePrivateNoteMapper;
        this.talentDomainFacade = talentDomainFacade;
        this.actionPolicy = actionPolicy;
        this.remarkPolicy = remarkPolicy;
    }

    public SampleEditContextVO getEditContext(
            UUID sampleId,
            UUID currentUserId,
            UUID currentDeptId,
            DataScope dataScope,
            Object roleCodes) {
        SampleVO visible = requireVisibleSample(
                sampleId, currentUserId, currentDeptId, dataScope, roleCodes);
        SampleRequest sample = requireSample(sampleId);
        return buildEditContext(sample, visible);
    }

    @Transactional(rollbackFor = Exception.class)
    public SampleEditContextVO updateCooperationDetails(
            UUID sampleId,
            SampleCooperationUpdateRequest request,
            UUID currentUserId,
            UUID currentDeptId,
            DataScope dataScope,
            Object roleCodes) {
        if (request == null || request.version() == null) {
            throw BusinessException.param("version 不能为空");
        }
        SampleVO visible = requireVisibleSample(
                sampleId, currentUserId, currentDeptId, dataScope, roleCodes);
        SampleRequest sample = requireSample(sampleId);
        SampleStatus status = SampleStatus.fromCode(sample.getStatus());
        actionPolicy.ensureCanEdit(status, sample.getUserId(), currentUserId, roleCodes);

        AddressUpdate address = normalizeAddress(request);
        sample.setVersion(request.version());
        remarkPolicy.apply(sample, request.remark());
        if (address.present()) {
            sample.setRecipientName(address.recipientName());
            sample.setRecipientPhone(address.recipientPhone());
            sample.setRecipientAddress(address.recipientAddress());
        }

        OptimisticLockSupport.requireUpdated(
                sampleRequestMapper.updateById(sample),
                "合作详情已被修改，请刷新后重试");

        if (address.present()) {
            talentDomainFacade.updateActiveClaimAddress(
                    sample.getTalentId(),
                    sample.getUserId(),
                    address.recipientName(),
                    address.recipientPhone(),
                    address.recipientAddress());
        }

        visible.setVersion(sample.getVersion());
        visible.setRemark(remarkPolicy.displayRemark(sample.getExtraData(), sample.getRemark()));
        visible.setApplyReason(visible.getRemark());
        if (address.present()) {
            visible.setRecipientName(address.recipientName());
            visible.setRecipientPhone(address.recipientPhone());
            visible.setRecipientAddress(address.recipientAddress());
        }
        return buildEditContext(sample, visible);
    }

    public SamplePrivateNoteVO getPrivateNote(
            UUID sampleId,
            UUID currentUserId,
            UUID currentDeptId,
            DataScope dataScope,
            Object roleCodes) {
        requireVisibleSample(sampleId, currentUserId, currentDeptId, dataScope, roleCodes);
        SamplePrivateNote note = samplePrivateNoteMapper.selectBySampleRequestAndUser(sampleId, currentUserId);
        return note == null
                ? new SamplePrivateNoteVO(null, null)
                : new SamplePrivateNoteVO(note.getContent(), note.getVersion());
    }

    @Transactional(rollbackFor = Exception.class)
    public SamplePrivateNoteVO updatePrivateNote(
            UUID sampleId,
            SamplePrivateNoteRequest request,
            UUID currentUserId,
            UUID currentDeptId,
            DataScope dataScope,
            Object roleCodes) {
        requireVisibleSample(sampleId, currentUserId, currentDeptId, dataScope, roleCodes);
        String content = remarkPolicy.normalize(request == null ? null : request.content());
        SamplePrivateNote note = samplePrivateNoteMapper.selectBySampleRequestAndUser(sampleId, currentUserId);

        if (content == null) {
            if (note != null) {
                note.setDeleted(1);
                OptimisticLockSupport.requireUpdated(
                        samplePrivateNoteMapper.updateById(note),
                        "私有备注已被修改，请刷新后重试");
            }
            return new SamplePrivateNoteVO(null, note == null ? null : note.getVersion());
        }

        if (note == null) {
            note = new SamplePrivateNote();
            note.setId(UUID.randomUUID());
            note.setSampleRequestId(sampleId);
            note.setUserId(currentUserId);
            note.setVersion(0);
            note.setContent(content);
            OptimisticLockSupport.requireUpdated(
                    samplePrivateNoteMapper.insert(note),
                    "私有备注保存失败，请重试");
        } else {
            note.setContent(content);
            OptimisticLockSupport.requireUpdated(
                    samplePrivateNoteMapper.updateById(note),
                    "私有备注已被修改，请刷新后重试");
        }
        return new SamplePrivateNoteVO(note.getContent(), note.getVersion());
    }

    private SampleVO requireVisibleSample(
            UUID sampleId,
            UUID currentUserId,
            UUID currentDeptId,
            DataScope dataScope,
            Object roleCodes) {
        return sampleQueryApplicationService.getSampleById(
                sampleId, currentUserId, currentDeptId, dataScope, roleCodes);
    }

    private SampleRequest requireSample(UUID sampleId) {
        SampleRequest sample = sampleRequestMapper.selectById(sampleId);
        if (sample == null) {
            throw BusinessException.notFound("Sample request not found");
        }
        return sample;
    }

    private SampleEditContextVO buildEditContext(SampleRequest sample, SampleVO visible) {
        TalentReadDTO talent = talentDomainFacade.findTalentById(sample.getTalentId());
        TalentClaimAddressDTO address = talentDomainFacade.findActiveClaimAddress(
                sample.getTalentId(), sample.getUserId());
        Map<String, Object> threshold = readMap(sample.getExtraData(), "requirementSnapshot");
        String recipientName = address == null ? null : trimToNull(address.recipientName());
        String recipientPhone = address == null ? null : trimToNull(address.recipientPhone());
        String recipientAddress = address == null ? null : trimToNull(address.recipientAddress());
        boolean addressAvailable = recipientName != null
                && recipientPhone != null
                && recipientAddress != null;
        return new SampleEditContextVO(
                sample.getId(),
                firstText(talent == null ? null : talent.nickname(), visible.getTalentName()),
                talent == null ? null : talent.douyinNo(),
                talent == null ? visible.getTalentFansCount() : talent.fansCount(),
                talent == null ? null : talent.windowSales30d(),
                visible.getProductId(),
                visible.getProductExternalId(),
                visible.getProductName(),
                visible.getShopName(),
                readText(sample.getExtraData(), "specification"),
                visible.getQuantity(),
                threshold,
                firstText(sample.getActivityId(), visible.getActivityId()),
                readText(sample.getExtraData(), "activityName"),
                remarkPolicy.displayRemark(sample.getExtraData(), sample.getRemark()),
                addressAvailable,
                recipientName,
                recipientPhone,
                recipientAddress,
                sample.getVersion());
    }

    private AddressUpdate normalizeAddress(SampleCooperationUpdateRequest request) {
        String name = trimToNull(request.recipientName());
        String phone = trimToNull(request.recipientPhone());
        String address = trimToNull(request.recipientAddress());
        int present = (name == null ? 0 : 1) + (phone == null ? 0 : 1) + (address == null ? 0 : 1);
        if (present != 0 && present != 3) {
            throw BusinessException.param("收件人、手机号和地址必须同时填写");
        }
        return new AddressUpdate(present == 3, name, phone, address);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(Map<String, Object> extraData, String key) {
        if (extraData == null) {
            return null;
        }
        Object value = extraData.get(key);
        if (value instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return null;
    }

    private String readText(Map<String, Object> extraData, String key) {
        if (extraData == null) {
            return null;
        }
        Object value = extraData.get(key);
        return value == null ? null : trimToNull(String.valueOf(value));
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first.trim() : trimToNull(second);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record AddressUpdate(
            boolean present,
            String recipientName,
            String recipientPhone,
            String recipientAddress) {
    }
}
