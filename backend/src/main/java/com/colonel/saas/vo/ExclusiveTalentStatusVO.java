package com.colonel.saas.vo;

import com.colonel.saas.entity.ExclusiveTalent;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 独家达人心情展示视图对象。
 * <p>
 * 用于独家达人管理页面展示达人的独家合作状态信息，包括服务费、渠道总费用、
 * 服务费率、每月寄样额度等关键商业指标。由 {@code ExclusiveTalent} 实体转换而来。
 * </p>
 * <p>
 * V1 阶段独家评估由 {@code ExclusiveEvaluateJob} 定时触发，根据订单数据判断
 * 达人是否满足独家条件。
 * </p>
 *
 * @see com.colonel.saas.entity.ExclusiveTalent
 * @see com.colonel.saas.job.ExclusiveEvaluateJob
 */
@Data
public class ExclusiveTalentStatusVO {
    /** 独家关系记录 ID */
    private String id;
    /** 达人 ID */
    private String talentId;
    /** 达人抖音 UID */
    private String talentUid;
    /** 关联的系统用户 ID */
    private String userId;
    /** 独家类型，标识独家合作的具体模式 */
    private Integer exclusiveType;
    /** 生效月份，格式：yyyy-MM */
    private String effectiveMonth;
    /** 服务费，单位：分 */
    private Long serviceFee;
    /** 渠道总费用，单位：分 */
    private Long channelTotalFee;
    /** 服务费率 */
    private BigDecimal serviceFeeRatio;
    /** 每月寄样配额 */
    private Integer monthlySamples;
    /** 独家合作开始日期 */
    private LocalDate startDate;
    /** 独家合作结束日期 */
    private LocalDate endDate;
    /** 状态：标识独家合作是否生效 */
    private Integer status;
    /** 备注信息 */
    private String remark;
    /** 触发类型：标识独家关系的触发方式（自动评估/手动设置） */
    private Integer triggerType;

    /**
     * 从 {@code ExclusiveTalent} 实体转换为 VO。
     * <p>
     * 该方法处理 null 值的安全转换，UUID 类型字段转为 String 以适配前端展示。
     * </p>
     *
     * @param talent 独家达人实体，可以为 null
     * @return 对应的 VO 对象，输入为 null 时返回 null
     */
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
