package com.colonel.saas.domain.order.query;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 订单分页查询只读视图（DDD-ORDER-006）。
 * <p>
 * 用于隔离 Controller 和底层的数据库 Entity，只读字段映射到前端所需属性。
 * </p>
 */
@Data
public class OrderQueryView {
    private UUID id;
    private String orderId;
    private String productId;
    private String productName;
    private Long shopId;
    private String shopName;
    private Long orderAmount;
    private Long payAmount;
    private Long actualAmount;
    private Long settleAmount;
    private Long estimateServiceFee;
    private Long effectiveServiceFee;
    private Long estimateTechServiceFee;
    private Long effectiveTechServiceFee;
    private Long estimateServiceFeeExpense;
    private Long effectiveServiceFeeExpense;
    private Long colonelBuyinId;
    private Long settleColonelCommission;
    private Long settleColonelTechServiceFee;
    private Long secondColonelBuyinId;
    private String secondActivityId;
    private Long settleSecondColonelCommission;
    private String flowPoint;
    private String phaseId;
    private Integer orderStatus;
    private Integer orderType;
    private String pickSource;
    private String cursor;
    private UUID talentId;
    private UUID channelUserId;
    private String channelUserName;
    private UUID colonelUserId;
    private String colonelUserName;
    private UUID promotionLinkId;
    private String productTitle;
    private String productPic;
    private String productImage;
    private Integer itemNum;
    private Integer productQuantity;
    private BigDecimal commissionRate;
    private BigDecimal serviceFeeRate;
    private String channelId;
    private String channelName;
    private String awemeId;
    private String orderTypeText;
    private String contentTypeText;
    private String talentName;
    private UUID channelDeptId;
    private UUID userId;
    private UUID deptId;
    private String activityId;
    private LocalDateTime payTime;
    private LocalDateTime orderCreateTime;
    private LocalDateTime settleTime;
    private String attributionStatus;
    private String attributionRemark;
    private String unattributedReason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
