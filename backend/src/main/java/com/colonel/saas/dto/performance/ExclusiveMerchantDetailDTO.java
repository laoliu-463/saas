package com.colonel.saas.dto.performance;

import lombok.Data;

/**
 * 独家商家详情 DTO。
 * <p>
 * 表示某个商家的独家合作关系详情，包括合作方信息、招募人信息、有效期和状态。
 * 用于业绩域的独家商家管理与查询。
 * 关联业务领域：业绩域（Performance）。
 * </p>
 */
@Data
public class ExclusiveMerchantDetailDTO {
    /** 合作伙伴 ID */
    private String partnerId;
    /** 合作伙伴名称 */
    private String partnerName;
    /** 是否为独家合作 */
    private boolean exclusive;
    /** 招募人 ID */
    private String recruiterId;
    /** 招募人姓名 */
    private String recruiterName;
    /** 生效月份（格式：yyyy-MM） */
    private String effectiveMonth;
    /** 到期月份（格式：yyyy-MM） */
    private String expireMonth;
    /** 独家状态（如 active、expired） */
    private String status;
}
