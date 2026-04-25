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
@TableName(value = "promotion_link", autoResultMap = true)
public class PromotionLink implements Serializable {

    @TableId(type = IdType.INPUT)
    private UUID id;

    @TableField("product_id")
    private String productId;

    @TableField("activity_id")
    private String activityId;

    @TableField("talent_id")
    private String talentId;

    @TableField("talent_name")
    private String talentName;

    @TableField("channel_user_id")
    private UUID channelUserId;

    @TableField("channel_user_name")
    private String channelUserName;

    @TableField("original_product_url")
    private String originalProductUrl;

    @TableField("promotion_url")
    private String promotionUrl;

    @TableField("short_url")
    private String shortUrl;

    @TableField("doukouling")
    private String doukouling;

    @TableField("pick_source")
    private String pickSource;

    @TableField("pick_extra")
    private String pickExtra;

    @TableField("link_status")
    private String linkStatus;

    @TableField("expire_time")
    private LocalDateTime expireTime;

    @TableField(value = "raw_response", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> rawResponse;

    @TableField("operator_id")
    private UUID operatorId;

    @TableField("operator_name")
    private String operatorName;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    private Integer deleted;
}
