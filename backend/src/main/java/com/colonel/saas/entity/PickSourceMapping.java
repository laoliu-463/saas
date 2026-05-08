package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.base.BaseEntity;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pick_source_mapping")
public class PickSourceMapping extends BaseEntity {

    @TableField("user_id")
    private UUID userId;

    @TableField("short_id")
    private String shortId;

    @TableField("uuid_seed")
    private UUID uuidSeed;

    @TableField("dept_id")
    private UUID deptId;

    @TableField("pick_source")
    @Size(max = 128)
    private String pickSource;

    @TableField("product_id")
    private String productId;

    @TableField("activity_id")
    private String activityId;

    @TableField("source_url")
    private String sourceUrl;

    @TableField("converted_url")
    private String convertedUrl;

    @TableField("pick_extra")
    @Size(max = 128)
    private String pickExtra;

    @TableField("promotion_link_id")
    private UUID promotionLinkId;

    @TableField("channel_user_name")
    private String channelUserName;

    @TableField("talent_id")
    private String talentId;

    @TableField("talent_name")
    private String talentName;

    @TableField("scene")
    private String scene;

    @TableField("valid_from")
    private LocalDateTime validFrom;

    @TableField("valid_until")
    private LocalDateTime validUntil;

    private Integer status;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getShortId() {
        return shortId;
    }

    public void setShortId(String shortId) {
        this.shortId = shortId;
    }

    public UUID getUuidSeed() {
        return uuidSeed;
    }

    public void setUuidSeed(UUID uuidSeed) {
        this.uuidSeed = uuidSeed;
    }

    public UUID getDeptId() {
        return deptId;
    }

    public void setDeptId(UUID deptId) {
        this.deptId = deptId;
    }

    public String getPickSource() {
        return pickSource;
    }

    public void setPickSource(String pickSource) {
        this.pickSource = pickSource;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getActivityId() {
        return activityId;
    }

    public void setActivityId(String activityId) {
        this.activityId = activityId;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getConvertedUrl() {
        return convertedUrl;
    }

    public void setConvertedUrl(String convertedUrl) {
        this.convertedUrl = convertedUrl;
    }

    public String getPickExtra() {
        return pickExtra;
    }

    public void setPickExtra(String pickExtra) {
        this.pickExtra = pickExtra;
    }

    public LocalDateTime getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDateTime validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDateTime getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(LocalDateTime validUntil) {
        this.validUntil = validUntil;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
