package com.colonel.saas.vo;

import com.colonel.saas.entity.ExclusiveTalent;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ExclusiveTalentStatusVO {
    private String id;
    private String talentId;
    private String talentUid;
    private String userId;
    private Integer exclusiveType;
    private String effectiveMonth;
    private Long serviceFee;
    private Long channelTotalFee;
    private BigDecimal serviceFeeRatio;
    private Integer monthlySamples;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer status;
    private String remark;
    private Integer triggerType;

    public static ExclusiveTalentStatusVO from(ExclusiveTalent talent) {
        if (talent == null) {
            return null;
        }
        ExclusiveTalentStatusVO vo = new ExclusiveTalentStatusVO();
        vo.setId(talent.getId() == null ? null : talent.getId().toString());
        vo.setTalentId(talent.getTalentId() == null ? null : talent.getTalentId().toString());
        vo.setTalentUid(talent.getTalentUid());
        vo.setUserId(talent.getUserId() == null ? null : talent.getUserId().toString());
        vo.setExclusiveType(talent.getExclusiveType());
        vo.setEffectiveMonth(talent.getEffectiveMonth());
        vo.setServiceFee(talent.getServiceFee());
        vo.setChannelTotalFee(talent.getChannelTotalFee());
        vo.setServiceFeeRatio(talent.getServiceFeeRatio());
        vo.setMonthlySamples(talent.getMonthlySamples());
        vo.setStartDate(talent.getStartDate());
        vo.setEndDate(talent.getEndDate());
        vo.setStatus(talent.getStatus());
        vo.setRemark(talent.getRemark());
        vo.setTriggerType(talent.getTriggerType());
        return vo;
    }
}
