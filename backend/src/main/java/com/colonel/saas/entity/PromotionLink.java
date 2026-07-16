package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.handler.UUIDTypeHandler;
import com.colonel.saas.common.typehandler.JsonbTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 推广链接实体。
 * <p>
 * 对应数据库表：{@code promotion_link}，记录商品的抖店推广链接信息。
 * 推广链接包含普通推广链接、短链接、抖口令等多种形式，关联商品、活动和达人上下文。
 * 通过 pickSource 标识推广来源，用于订单归属（attribution）和业绩计算。
 * 不继承 BaseEntity，采用手动输入的 UUID 主键，实现 Serializable 接口。
 * </p>
 *
 * @see PickSourceMapping 推广来源映射
 * @see Product 商品主实体
 * @see ColonelsettlementActivity 团长活动
 */
@Data
@TableName(value = "promotion_link", autoResultMap = true)
public class PromotionLink implements Serializable {

    /**
     * 主键 ID
     * <p>手动输入的 UUID 主键，由 UUIDTypeHandler 负责类型转换</p>
     */
    @TableId(type = IdType.INPUT)
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID id;

    /**
     * 商品 ID
     * <p>对应数据库列：{@code product_id}，关联商品主表，标识推广链接所对应的商品</p>
     */
    @TableField("product_id")
    private String productId;

    /**
     * 活动 ID
     * <p>对应数据库列：{@code activity_id}，关联团长活动，标识推广链接生成的活动上下文</p>
     */
    @TableField("activity_id")
    private String activityId;

    /**
     * 达人 ID
     * <p>对应数据库列：{@code talent_id}，关联达人，标识推广链接对应的达人</p>
     */
    @TableField("talent_id")
    private String talentId;

    /**
     * 达人名称
     * <p>对应数据库列：{@code talent_name}，冗余存储达人昵称，便于列表展示</p>
     */
    @TableField("talent_name")
    private String talentName;

    /**
     * 渠道用户 ID
     * <p>对应数据库列：{@code channel_user_id}，推广链接归属的系统用户标识，
     * 即生成该链接的业务人员</p>
     */
    @TableField("channel_user_id")
    private UUID channelUserId;

    /**
     * 渠道用户名称
     * <p>对应数据库列：{@code channel_user_name}，冗余存储渠道用户姓名，便于展示</p>
     */
    @TableField("channel_user_name")
    private String channelUserName;

    /** 创建链接时固化的归属维度：CHANNEL 或 RECRUITER。 */
    @TableField("attribution_owner_type")
    private String attributionOwnerType;

    /** 创建链接时固化的归属输入快照，供订单及业绩归因审计解释。 */
    @TableField(value = "attribution_snapshot", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> attributionSnapshot;

    /**
     * 原始商品链接
     * <p>对应数据库列：{@code original_product_url}，转链前的原始商品 URL</p>
     */
    @TableField("original_product_url")
    private String originalProductUrl;

    /**
     * 推广链接
     * <p>对应数据库列：{@code promotion_url}，转链后的抖店推广长链接</p>
     */
    @TableField("promotion_url")
    private String promotionUrl;

    /**
     * 短链接
     * <p>对应数据库列：{@code short_url}，推广链接的短链形式，便于分享传播</p>
     */
    @TableField("short_url")
    private String shortUrl;

    /**
     * 抖口令
     * <p>对应数据库列：{@code doukouling}，抖店平台的口令推广形式，
     * 用户复制口令后打开抖音 APP 可直达商品页</p>
     */
    @TableField("doukouling")
    private String doukouling;

    /**
     * 推广来源标识
     * <p>对应数据库列：{@code pick_source}，标识推广链接的来源渠道，
     * 用于订单归属时关联团长和达人</p>
     */
    @TableField("pick_source")
    private String pickSource;

    /**
     * 推广扩展信息
     * <p>对应数据库列：{@code pick_extra}，推广来源的附加参数，
     * 如达人 UID、渠道编码等扩展字段</p>
     */
    @TableField("pick_extra")
    private String pickExtra;

    /**
     * 链接状态
     * <p>对应数据库列：{@code link_status}，推广链接的当前状态，
     * 如 "ACTIVE"（有效）、"EXPIRED"（已过期）、"DISABLED"（已禁用）等</p>
     */
    @TableField("link_status")
    private String linkStatus;

    /**
     * 过期时间
     * <p>对应数据库列：{@code expire_time}，推广链接的失效时间，
     * 超过该时间后链接状态变更为已过期</p>
     */
    @TableField("expire_time")
    private LocalDateTime expireTime;

    /**
     * 原始响应
     * <p>JSON 格式，对应数据库列：{@code raw_response}，抖店平台转链接口的原始返回数据，
     * 由 JacksonTypeHandler 自动序列化/反序列化，用于问题排查</p>
     */
    @TableField(value = "raw_response", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> rawResponse;

    /**
     * 操作人 ID
     * <p>对应数据库列：{@code operator_id}，生成该推广链接的操作人标识</p>
     */
    @TableField("operator_id")
    private UUID operatorId;

    /**
     * 操作人名称
     * <p>对应数据库列：{@code operator_name}，冗余存储操作人姓名，便于展示</p>
     */
    @TableField("operator_name")
    private String operatorName;

    /**
     * 创建时间
     * <p>对应数据库列：{@code created_at}，推广链接的生成时间</p>
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     * <p>对应数据库列：{@code updated_at}，推广链接最后一次变更的时间</p>
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除标记
     * <p>0=未删除, 1=已删除</p>
     */
    private Integer deleted;
}
