package com.colonel.saas.domain.sample.application;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.enums.SampleStatus;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.OptimisticLockSupport;
import com.colonel.saas.domain.product.facade.ProductPromotionFacade;
import com.colonel.saas.domain.product.facade.dto.ProductPromotionCopyDTO;
import com.colonel.saas.domain.sample.policy.SampleCooperationActionPolicy;
import com.colonel.saas.domain.sample.policy.SampleOrderCopyPolicy;
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
import com.colonel.saas.vo.sample.SampleCopyTextVO;
import com.colonel.saas.vo.sample.SamplePrivateNoteVO;
import com.colonel.saas.vo.sample.SampleVO;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final ProductPromotionFacade productPromotionFacade;
    private final SampleOrderCopyPolicy orderCopyPolicy;

    /** 保留 Task 3 既有测试和非 Spring 调用方的构造方式。 */
    public SampleCooperationApplicationService(
            SampleQueryApplicationService sampleQueryApplicationService,
            SampleRequestMapper sampleRequestMapper,
            SamplePrivateNoteMapper samplePrivateNoteMapper,
            TalentDomainFacade talentDomainFacade,
            SampleCooperationActionPolicy actionPolicy,
            SampleRemarkPolicy remarkPolicy) {
        this(
                sampleQueryApplicationService,
                sampleRequestMapper,
                samplePrivateNoteMapper,
                talentDomainFacade,
                actionPolicy,
                remarkPolicy,
                null,
                new SampleOrderCopyPolicy());
    }

    /** Spring 运行路径使用包含商品推广门面的完整构造器。 */
    @Autowired
    public SampleCooperationApplicationService(
            SampleQueryApplicationService sampleQueryApplicationService,
            SampleRequestMapper sampleRequestMapper,
            SamplePrivateNoteMapper samplePrivateNoteMapper,
            TalentDomainFacade talentDomainFacade,
            SampleCooperationActionPolicy actionPolicy,
            SampleRemarkPolicy remarkPolicy,
            ProductPromotionFacade productPromotionFacade,
            SampleOrderCopyPolicy orderCopyPolicy) {
        this.sampleQueryApplicationService = sampleQueryApplicationService;
        this.sampleRequestMapper = sampleRequestMapper;
        this.samplePrivateNoteMapper = samplePrivateNoteMapper;
        this.talentDomainFacade = talentDomainFacade;
        this.actionPolicy = actionPolicy;
        this.remarkPolicy = remarkPolicy;
        this.productPromotionFacade = productPromotionFacade;
        this.orderCopyPolicy = orderCopyPolicy;
    }

    /**
     * 基于当前用户可见的寄样事实生成抖音推广复制文本。
     */
    public SampleCopyTextVO copyPromotion(
            UUID sampleId,
            UUID currentUserId,
            UUID currentDeptId,
            DataScope dataScope,
            Object roleCodes,
            String idempotencyKey) {
        SampleVO visible = requireVisibleSample(
                sampleId, currentUserId, currentDeptId, dataScope, roleCodes);
        if (productPromotionFacade == null) {
            throw BusinessException.stateInvalid("商品推广复制能力不可用，请检查应用服务装配");
        }

        String activityId = trimToNull(visible.getActivityId());
        String productId = trimToNull(visible.getProductExternalId());
        String talentId = firstText(
                visible.getTalentUid(),
                visible.getTalentId() == null ? null : visible.getTalentId().toString());
        if (activityId == null || productId == null || talentId == null) {
            throw BusinessException.stateInvalid("寄样单缺少活动、商品或达人推广事实，暂无法复制推广文案");
        }
        String requestKey = trimToNull(idempotencyKey);
        if (requestKey == null) {
            requestKey = UUID.randomUUID().toString();
        }
        ProductPromotionCopyDTO result = productPromotionFacade.copyForSample(
                activityId,
                productId,
                currentUserId,
                currentDeptId,
                talentId,
                requestKey);
        if (result == null) {
            throw BusinessException.conflict("商品推广复制未返回结果，请重试");
        }
        return new SampleCopyTextVO(
                result.text(),
                result.promotionLinkGenerated(),
                result.promotionLink(),
                result.fallbackReason());
    }

    /**
     * 基于可见寄样单、商品快照读模型和合作 owner 的达人事实生成订单文本。
     */
    public SampleCopyTextVO copyOrder(
            UUID sampleId,
            UUID currentUserId,
            UUID currentDeptId,
            DataScope dataScope,
            Object roleCodes) {
        SampleVO visible = requireVisibleSample(
                sampleId, currentUserId, currentDeptId, dataScope, roleCodes);
        SampleRequest sample = requireSample(sampleId);
        SampleEditContextVO context = buildEditContext(sample, visible);
        String text = orderCopyPolicy.format(new SampleOrderCopyPolicy.OrderCopyFacts(
                context.productName(),
                context.productExternalId(),
                context.shopName(),
                sample.getExpectedSampleNum(),
                context.productSpecification(),
                context.remark(),
                context.talentNickname(),
                context.talentDouyinNo(),
                context.talentFansCount(),
                context.talentWindowSales30d(),
                context.recipientName(),
                context.recipientPhone(),
                context.recipientAddress()));
        return new SampleCopyTextVO(text);
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
        visible.setRemark(remarkPolicy.resolve(sample.getExtraData(), sample.getRemark()));
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
        String content = remarkPolicy.normalizeForWrite(request == null ? null : request.content());

        if (content == null) {
            SamplePrivateNote note = samplePrivateNoteMapper.selectBySampleRequestAndUser(sampleId, currentUserId);
            if (note != null) {
                OptimisticLockSupport.requireUpdated(
                        samplePrivateNoteMapper.softDeleteActive(
                                note.getId(), currentUserId, note.getVersion()),
                        "私有备注已被修改，请刷新后重试");
            }
            Integer deletedVersion = note == null || note.getVersion() == null
                    ? null
                    : note.getVersion() + 1;
            return new SamplePrivateNoteVO(null, deletedVersion);
        }

        SamplePrivateNote candidate = new SamplePrivateNote();
        candidate.setId(UUID.randomUUID());
        candidate.setSampleRequestId(sampleId);
        candidate.setUserId(currentUserId);
        candidate.setVersion(0);
        candidate.setContent(content);
        candidate.setCreateBy(currentUserId);
        candidate.setUpdateBy(currentUserId);
        OptimisticLockSupport.requireUpdated(
                samplePrivateNoteMapper.upsertActive(candidate),
                "私有备注保存失败，请重试");
        SamplePrivateNote persisted =
                samplePrivateNoteMapper.selectBySampleRequestAndUser(sampleId, currentUserId);
        if (persisted == null) {
            throw BusinessException.conflict("私有备注保存失败，请重试");
        }
        return new SamplePrivateNoteVO(persisted.getContent(), persisted.getVersion());
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
                remarkPolicy.resolve(sample.getExtraData(), sample.getRemark()),
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
