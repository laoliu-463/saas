package com.colonel.saas.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 合作伙伴列表展示视图对象。
 * <p>
 * 用于合作伙伴列表页面的展示，包含合作伙伴的基本信息和关联商品数量。
 * 合作伙伴是指与团长存在合作关系的抖店商家，通过抖店 API 同步而来。
 * </p>
 *
 * @see com.colonel.saas.mapper.ColonelPartnerMapper
 */
@Data
public class PartnerVO {
    /** 合作伙伴 ID */
    private String partnerId;
    /** 合作伙伴名称（商家名称） */
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
    /** 状态：标识合作是否有效 */
    private Integer status;
}
