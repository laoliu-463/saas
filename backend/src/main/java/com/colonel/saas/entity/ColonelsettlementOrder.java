package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 订单分区表实体（LCK-01）。不继承 {@link com.colonel.saas.common.base.VersionedEntity}，
 * 因表无 create_by/update_by 且主键含 create_time 分区键。
 */
@Data
@TableName(value = "colonelsettlement_order", autoResultMap = true)
public class ColonelsettlementOrder implements Serializable {

    @TableId(type = IdType.INPUT)
    private UUID id;

    @TableField("order_id")
    private String orderId;

    @TableField("product_id")
    private String productId;

    @TableField("product_name")
    private String productName;

    @TableField("shop_id")
    private Long shopId;

    @TableField("shop_name")
    private String shopName;

    @TableField("order_amount")
    private Long orderAmount;

    @TableField("actual_amount")
    private Long actualAmount;

    @TableField("settle_amount")
    private Long settleAmount;

    @TableField("estimate_service_fee")
    private Long estimateServiceFee;

    @TableField("effective_service_fee")
    private Long effectiveServiceFee;

    @TableField("estimate_tech_service_fee")
    private Long estimateTechServiceFee;

    @TableField("effective_tech_service_fee")
    private Long effectiveTechServiceFee;

    @TableField("colonel_buyin_id")
    private Long colonelBuyinId;

    @TableField("settle_colonel_commission")
    private Long settleColonelCommission;

    @TableField("settle_colonel_tech_service_fee")
    private Long settleColonelTechServiceFee;

    @TableField("second_colonel_buyin_id")
    private Long secondColonelBuyinId;

    @TableField("second_colonel_activity_id")
    private String secondActivityId;

    @TableField("settle_second_colonel_commission")
    private Long settleSecondColonelCommission;

    @TableField("phase_id")
    private String phaseId;

    @TableField("order_status")
    private Integer orderStatus;

    @TableField("order_type")
    private Integer orderType;

    @TableField("pick_source")
    private String pickSource;

    @TableField("cursor")
    private String cursor;

    @TableField("talent_id")
    private UUID talentId;

    @TableField("channel_user_id")
    private UUID channelUserId;

    @TableField("channel_user_name")
    private String channelUserName;

    @TableField("colonel_user_id")
    private UUID colonelUserId;

    @TableField("colonel_user_name")
    private String colonelUserName;

    @TableField("promotion_link_id")
    private UUID promotionLinkId;

    @TableField("product_title")
    private String productTitle;

    @TableField("talent_name")
    private String talentName;

    @TableField("channel_dept_id")
    private UUID channelDeptId;

    @TableField("user_id")
    private UUID userId;

    @TableField("dept_id")
    private UUID deptId;

    @TableField("colonel_activity_id")
    private String activityId;

    @TableField("settle_time")
    private LocalDateTime settleTime;

    @TableField("attribution_status")
    private String attributionStatus;

    @TableField("attribution_remark")
    private String attributionRemark;

    @TableField(exist = false)
    private String unattributedReason;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    @Version
    @TableField("version")
    private Integer version;

    private Integer deleted;

    @TableField(value = "extra_data", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraData;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public Long getShopId() {
        return shopId;
    }

    public void setShopId(Long shopId) {
        this.shopId = shopId;
    }

    public String getShopName() {
        return shopName;
    }

    public void setShopName(String shopName) {
        this.shopName = shopName;
    }

    public Long getOrderAmount() {
        return orderAmount;
    }

    public void setOrderAmount(Long orderAmount) {
        this.orderAmount = orderAmount;
    }

    public Long getActualAmount() {
        return actualAmount;
    }

    public void setActualAmount(Long actualAmount) {
        this.actualAmount = actualAmount;
    }

    public Long getColonelBuyinId() {
        return colonelBuyinId;
    }

    public void setColonelBuyinId(Long colonelBuyinId) {
        this.colonelBuyinId = colonelBuyinId;
    }

    public Long getSettleColonelCommission() {
        return settleColonelCommission;
    }

    public void setSettleColonelCommission(Long settleColonelCommission) {
        this.settleColonelCommission = settleColonelCommission;
    }

    public Long getSettleColonelTechServiceFee() {
        return settleColonelTechServiceFee;
    }

    public void setSettleColonelTechServiceFee(Long settleColonelTechServiceFee) {
        this.settleColonelTechServiceFee = settleColonelTechServiceFee;
    }

    public Long getSecondColonelBuyinId() {
        return secondColonelBuyinId;
    }

    public void setSecondColonelBuyinId(Long secondColonelBuyinId) {
        this.secondColonelBuyinId = secondColonelBuyinId;
    }

    public String getSecondActivityId() {
        return secondActivityId;
    }

    public void setSecondActivityId(String secondActivityId) {
        this.secondActivityId = secondActivityId;
    }

    public Long getSettleSecondColonelCommission() {
        return settleSecondColonelCommission;
    }

    public void setSettleSecondColonelCommission(Long settleSecondColonelCommission) {
        this.settleSecondColonelCommission = settleSecondColonelCommission;
    }

    public String getPhaseId() {
        return phaseId;
    }

    public void setPhaseId(String phaseId) {
        this.phaseId = phaseId;
    }

    public Integer getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(Integer orderStatus) {
        this.orderStatus = orderStatus;
    }

    public Integer getOrderType() {
        return orderType;
    }

    public void setOrderType(Integer orderType) {
        this.orderType = orderType;
    }

    public String getPickSource() {
        return pickSource;
    }

    public void setPickSource(String pickSource) {
        this.pickSource = pickSource;
    }

    public String getCursor() {
        return cursor;
    }

    public void setCursor(String cursor) {
        this.cursor = cursor;
    }

    public UUID getChannelUserId() {
        return channelUserId;
    }

    public void setChannelUserId(UUID channelUserId) {
        this.channelUserId = channelUserId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getChannelDeptId() {
        return channelDeptId;
    }

    public void setChannelDeptId(UUID channelDeptId) {
        this.channelDeptId = channelDeptId;
    }

    public UUID getDeptId() {
        return deptId;
    }

    public void setDeptId(UUID deptId) {
        this.deptId = deptId;
    }

    public String getActivityId() {
        return activityId;
    }

    public void setActivityId(String activityId) {
        this.activityId = activityId;
    }

    public LocalDateTime getSettleTime() {
        return settleTime;
    }

    public void setSettleTime(LocalDateTime settleTime) {
        this.settleTime = settleTime;
    }

    public String getAttributionStatus() {
        return attributionStatus;
    }

    public void setAttributionStatus(String attributionStatus) {
        this.attributionStatus = attributionStatus;
    }

    public String getAttributionRemark() {
        return attributionRemark;
    }

    public void setAttributionRemark(String attributionRemark) {
        this.attributionRemark = attributionRemark;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public Map<String, Object> getExtraData() {
        return extraData;
    }

    public void setExtraData(Map<String, Object> extraData) {
        this.extraData = extraData;
    }
}
