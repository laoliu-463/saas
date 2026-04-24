package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

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

    @TableField("settle_colonel_commission")
    private Long settleColonelCommission;

    @TableField("settle_colonel_tech_service_fee")
    private Long settleColonelTechServiceFee;

    @TableField("settle_second_colonel_commission")
    private Long settleSecondColonelCommission;

    @TableField("order_status")
    private Integer orderStatus;

    @TableField("pick_source")
    private String pickSource;

    @TableField("channel_user_id")
    private UUID channelUserId;

    @TableField("user_id")
    private UUID userId;

    @TableField("dept_id")
    private UUID deptId;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

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

    public Long getSettleSecondColonelCommission() {
        return settleSecondColonelCommission;
    }

    public void setSettleSecondColonelCommission(Long settleSecondColonelCommission) {
        this.settleSecondColonelCommission = settleSecondColonelCommission;
    }

    public Integer getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(Integer orderStatus) {
        this.orderStatus = orderStatus;
    }

    public String getPickSource() {
        return pickSource;
    }

    public void setPickSource(String pickSource) {
        this.pickSource = pickSource;
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

    public UUID getDeptId() {
        return deptId;
    }

    public void setDeptId(UUID deptId) {
        this.deptId = deptId;
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
