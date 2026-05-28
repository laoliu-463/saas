package com.colonel.saas.vo;

import com.colonel.saas.entity.ExclusiveMerchant;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 独家商家状态展示视图对象。
 * <p>
 * 用于独家商家管理页面展示商家的独家合作状态信息，包括服务费、业务总费用、
 * 服务费率等关键商业指标。由 {@code ExclusiveMerchant} 实体转换而来。
 * </p>
 * <p>
 * 独家商家与独家达人类似，通过费用和时间维度评估合作关系的紧密程度，
 * 用于招商策略制定和资源分配决策。
 * </p>
 *
 * @see com.colonel.saas.entity.ExclusiveMerchant
 * @see com.colonel.saas.mapper.ExclusiveMerchantMapper
 */
@Data
public class ExclusiveMerchantStatusVO {
    /** 独家关系记录 ID */
    private String id;
    /** 商家 ID */
    private String merchantId;
    /** 商家名称 */
    private String merchantName;
    /** 抖店店铺 ID */
    private Long shopId;
    /** 关联的系统用户 ID */
    private String userId;
    /** 生效月份，格式：yyyy-MM */
    private String effectiveMonth;
    /** 服务费，单位：分 */
    private Long serviceFee;
    /** 业务总费用，单位：分 */
    private Long businessTotalFee;
    /** 服务费率 */
    private BigDecimal serviceFeeRatio;
    /** 独家合作开始日期 */
    private LocalDate startDate;
    /** 独家合作结束日期 */
    private LocalDate endDate;
    /** 状态：标识独家合作是否生效 */
    private Integer status;
    /** 备注信息 */
    private String remark;

    /**
     * 从 {@code ExclusiveMerchant} 实体转换为 VO。
     * <p>
     * 该方法处理 null 值的安全转换，UUID 类型字段转为 String 以适配前端展示。
     * </p>
     *
     * @param merchant 独家商家实体，可以为 null
     * @return 对应的 VO 对象，输入为 null 时返回 null
     */
    public static ExclusiveMerchantStatusVO from(ExclusiveMerchant merchant) {
        if (merchant == null) {
            return null;
        }
        ExclusiveMerchantStatusVO vo = new ExclusiveMerchantStatusVO();
        vo.setId(merchant.getId() == null ? null : merchant.getId().toString());
        vo.setMerchantId(merchant.getMerchantId());
        vo.setMerchantName(merchant.getMerchantName());
        vo.setShopId(merchant.getShopId());
        vo.setUserId(merchant.getUserId() == null ? null : merchant.getUserId().toString());
        vo.setEffectiveMonth(merchant.getEffectiveMonth());
        vo.setServiceFee(merchant.getServiceFee());
        vo.setBusinessTotalFee(merchant.getBusinessTotalFee());
        vo.setServiceFeeRatio(merchant.getServiceFeeRatio());
        vo.setStartDate(merchant.getStartDate());
        vo.setEndDate(merchant.getEndDate());
        vo.setStatus(merchant.getStatus());
        vo.setRemark(merchant.getRemark());
        return vo;
    }
}
