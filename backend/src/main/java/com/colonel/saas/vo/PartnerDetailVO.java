package com.colonel.saas.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 合作伙伴详情展示视图对象。
 * <p>
 * 用于合作伙伴详情页面的展示，包含合作伙伴的完整信息。
 * 与 {@link PartnerVO} 结构类似，但用于详情页场景，后续可扩展更多字段
 * （如合作历史、结算统计等）。
 * </p>
 *
 * @see PartnerVO
 */
@Data
public class PartnerDetailVO {
    /** 合作伙伴 ID */
    private String partnerId;
    /** 合作伙伴名称 */
    private String partnerName;
    /** 合作伙伴类型 */
    private String partnerType;
    /** 关联的抖店店铺 ID */
    private Long shopId;
    /** 关联的抖店店铺名称 */
    private String shopName;
    /** 关联商品数量 */
    private Long productCount;
    /** 最近一次数据同步时间 */
    private LocalDateTime latestSyncTime;
    /** 状态 */
    private Integer status;
}
