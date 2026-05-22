package com.colonel.saas.vo;

import com.colonel.saas.entity.ExclusiveMerchant;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ExclusiveMerchantStatusVO {
    private String id;
    private String merchantId;
    private String merchantName;
    private Long shopId;
    private String userId;
    private String effectiveMonth;
    private Long serviceFee;
    private Long businessTotalFee;
    private BigDecimal serviceFeeRatio;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer status;
    private String remark;

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
