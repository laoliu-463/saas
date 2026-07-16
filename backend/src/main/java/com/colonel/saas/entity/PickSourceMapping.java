package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.base.VersionedEntity;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 推广来源映射实体。
 * <p>
 * 对应数据库表：{@code pick_source_mapping}，记录推广来源标识（pickSource）与业务上下文
 * （用户、商品、活动、达人）之间的映射关系。当订单通过推广链接成交时，系统根据
 * pickSource 查找此映射表，完成订单归属（attribution）和业绩计算。
 * 映射记录支持有效期控制（validFrom / validUntil）和场景标识（scene）。
 * 继承 {@link com.colonel.saas.common.base.VersionedEntity}，拥有乐观锁支持。
 * </p>
 *
 * @see PromotionLink 推广链接
 * @see ColonelsettlementOrder 结算订单，使用 pickSource 进行归属
 * @see PerformanceRecord 业绩记录
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pick_source_mapping")
public class PickSourceMapping extends VersionedEntity {

    /**
     * 负责人用户 ID
     * <p>对应数据库列：{@code user_id}，该推广来源归属的业务负责人</p>
     */
    @TableField("user_id")
    private UUID userId;

    /**
     * 短标识
     * <p>对应数据库列：{@code short_id}，用于生成简短的 pickSource 编码</p>
     */
    @TableField("short_id")
    private String shortId;

    /**
     * UUID 种子
     * <p>对应数据库列：{@code uuid_seed}，生成 pickSource 时使用的 UUID 种子值，
     * 确保唯一性</p>
     */
    @TableField("uuid_seed")
    private UUID uuidSeed;

    /**
     * 部门 ID
     * <p>对应数据库列：{@code dept_id}，负责人所属部门，用于数据范围过滤</p>
     */
    @TableField("dept_id")
    private UUID deptId;

    /**
     * 推广来源标识
     * <p>对应数据库列：{@code pick_source}，全局唯一的推广来源编码，
     * 嵌入推广链接中，订单成交后通过此标识反查映射关系。
     * 最大长度 128 字符</p>
     */
    @TableField("pick_source")
    @Size(max = 128)
    private String pickSource;

    /**
     * 团长百应 ID
     * <p>对应数据库列：{@code colonel_buyin_id}，关联抖店平台的团长百应标识。
     * 最大长度 32 字符</p>
     */
    @TableField("colonel_buyin_id")
    @Size(max = 32)
    private String colonelBuyinId;

    /**
     * 商品 ID
     * <p>对应数据库列：{@code product_id}，推广来源所对应的商品标识</p>
     */
    @TableField("product_id")
    private String productId;

    /**
     * 活动 ID
     * <p>对应数据库列：{@code activity_id}，推广来源所对应的团长活动标识</p>
     */
    @TableField("activity_id")
    private String activityId;

    /**
     * 原始链接
     * <p>对应数据库列：{@code source_url}，转链前的商品原始 URL</p>
     */
    @TableField("source_url")
    private String sourceUrl;

    /**
     * 转链后链接
     * <p>对应数据库列：{@code converted_url}，转链后的推广链接 URL</p>
     */
    @TableField("converted_url")
    private String convertedUrl;

    /**
     * 推广扩展参数
     * <p>对应数据库列：{@code pick_extra}，附加的推广来源扩展信息。
     * 最大长度 128 字符</p>
     */
    @TableField("pick_extra")
    @Size(max = 128)
    private String pickExtra;

    /**
     * 推广链接 ID
     * <p>对应数据库列：{@code promotion_link_id}，关联推广链接表的主键</p>
     */
    @TableField("promotion_link_id")
    private UUID promotionLinkId;

    /**
     * 渠道用户名称
     * <p>对应数据库列：{@code channel_user_name}，冗余存储业务人员姓名，便于展示</p>
     */
    @TableField("channel_user_name")
    private String channelUserName;

    /** 创建推广链接时固化的归属维度：CHANNEL 或 RECRUITER。 */
    @TableField("attribution_owner_type")
    private String attributionOwnerType;

    /**
     * 达人 ID
     * <p>对应数据库列：{@code talent_id}，推广来源关联的达人标识</p>
     */
    @TableField("talent_id")
    private String talentId;

    /**
     * 达人名称
     * <p>对应数据库列：{@code talent_name}，冗余存储达人昵称，便于展示</p>
     */
    @TableField("talent_name")
    private String talentName;

    /**
     * 推广场景
     * <p>对应数据库列：{@code scene}，标识推广来源的业务场景，
     * 如 "PRODUCT_PROMOTE"（商品推广）、"TALENT_RECOMMEND"（达人推荐）等</p>
     */
    @TableField("scene")
    private String scene;

    /**
     * 来源类型
     * <p>对应数据库列：{@code source_type}，标识推广来源的生成方式，
     * 如 "MANUAL"（手动创建）、"AUTO"（系统自动生成）等</p>
     */
    @TableField("source_type")
    private String sourceType;

    /**
     * 生效开始时间
     * <p>对应数据库列：{@code valid_from}，推广来源映射的生效起始时间</p>
     */
    @TableField("valid_from")
    private LocalDateTime validFrom;

    /**
     * 生效截止时间
     * <p>对应数据库列：{@code valid_until}，推广来源映射的失效时间，
     * 超过此时间后映射不再用于订单归属</p>
     */
    @TableField("valid_until")
    private LocalDateTime validUntil;

    /**
     * 状态
     * <p>1=有效, 0=无效。用于手动禁用推广来源映射</p>
     */
    private Integer status;

    /**
     * 获取负责人用户 ID。
     *
     * @return 该推广来源归属的业务负责人标识
     */
    public UUID getUserId() {
        return userId;
    }

    /**
     * 设置负责人用户 ID。
     *
     * @param userId 该推广来源归属的业务负责人标识
     */
    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    /**
     * 获取短标识。
     *
     * @return 用于生成简短 pickSource 编码的短标识
     */
    public String getShortId() {
        return shortId;
    }

    /**
     * 设置短标识。
     *
     * @param shortId 用于生成简短 pickSource 编码的短标识
     */
    public void setShortId(String shortId) {
        this.shortId = shortId;
    }

    /**
     * 获取 UUID 种子。
     *
     * @return 生成 pickSource 时使用的 UUID 种子值
     */
    public UUID getUuidSeed() {
        return uuidSeed;
    }

    /**
     * 设置 UUID 种子。
     *
     * @param uuidSeed 生成 pickSource 时使用的 UUID 种子值
     */
    public void setUuidSeed(UUID uuidSeed) {
        this.uuidSeed = uuidSeed;
    }

    /**
     * 获取部门 ID。
     *
     * @return 负责人所属部门标识，用于数据范围过滤
     */
    public UUID getDeptId() {
        return deptId;
    }

    /**
     * 设置部门 ID。
     *
     * @param deptId 负责人所属部门标识
     */
    public void setDeptId(UUID deptId) {
        this.deptId = deptId;
    }

    /**
     * 获取推广来源标识。
     *
     * @return 全局唯一的推广来源编码，嵌入推广链接中用于订单归属
     */
    public String getPickSource() {
        return pickSource;
    }

    /**
     * 设置推广来源标识。
     *
     * @param pickSource 全局唯一的推广来源编码
     */
    public void setPickSource(String pickSource) {
        this.pickSource = pickSource;
    }

    /**
     * 获取团长百应 ID。
     *
     * @return 关联抖店平台的团长百应标识
     */
    public String getColonelBuyinId() {
        return colonelBuyinId;
    }

    /**
     * 设置团长百应 ID。
     *
     * @param colonelBuyinId 关联抖店平台的团长百应标识
     */
    public void setColonelBuyinId(String colonelBuyinId) {
        this.colonelBuyinId = colonelBuyinId;
    }

    /**
     * 获取商品 ID。
     *
     * @return 推广来源所对应的商品标识
     */
    public String getProductId() {
        return productId;
    }

    /**
     * 设置商品 ID。
     *
     * @param productId 推广来源所对应的商品标识
     */
    public void setProductId(String productId) {
        this.productId = productId;
    }

    /**
     * 获取活动 ID。
     *
     * @return 推广来源所对应的团长活动标识
     */
    public String getActivityId() {
        return activityId;
    }

    /**
     * 设置活动 ID。
     *
     * @param activityId 推广来源所对应的团长活动标识
     */
    public void setActivityId(String activityId) {
        this.activityId = activityId;
    }

    /**
     * 获取原始链接。
     *
     * @return 转链前的商品原始 URL
     */
    public String getSourceUrl() {
        return sourceUrl;
    }

    /**
     * 设置原始链接。
     *
     * @param sourceUrl 转链前的商品原始 URL
     */
    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    /**
     * 获取转链后链接。
     *
     * @return 转链后的推广链接 URL
     */
    public String getConvertedUrl() {
        return convertedUrl;
    }

    /**
     * 设置转链后链接。
     *
     * @param convertedUrl 转链后的推广链接 URL
     */
    public void setConvertedUrl(String convertedUrl) {
        this.convertedUrl = convertedUrl;
    }

    /**
     * 获取推广扩展参数。
     *
     * @return 附加的推广来源扩展信息
     */
    public String getPickExtra() {
        return pickExtra;
    }

    /**
     * 设置推广扩展参数。
     *
     * @param pickExtra 附加的推广来源扩展信息
     */
    public void setPickExtra(String pickExtra) {
        this.pickExtra = pickExtra;
    }

    /**
     * 获取生效开始时间。
     *
     * @return 推广来源映射的生效起始时间
     */
    public LocalDateTime getValidFrom() {
        return validFrom;
    }

    /**
     * 设置生效开始时间。
     *
     * @param validFrom 推广来源映射的生效起始时间
     */
    public void setValidFrom(LocalDateTime validFrom) {
        this.validFrom = validFrom;
    }

    /**
     * 获取生效截止时间。
     *
     * @return 推广来源映射的失效时间，超过此时间后不再用于订单归属
     */
    public LocalDateTime getValidUntil() {
        return validUntil;
    }

    /**
     * 设置生效截止时间。
     *
     * @param validUntil 推广来源映射的失效时间
     */
    public void setValidUntil(LocalDateTime validUntil) {
        this.validUntil = validUntil;
    }

    /**
     * 获取来源类型。
     *
     * @return 推广来源的生成方式，如 "MANUAL"（手动创建）、"AUTO"（系统自动生成）
     */
    public String getSourceType() {
        return sourceType;
    }

    /**
     * 设置来源类型。
     *
     * @param sourceType 推广来源的生成方式
     */
    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    /**
     * 获取状态。
     *
     * @return 状态值，1=有效，0=无效
     */
    public Integer getStatus() {
        return status;
    }

    /**
     * 设置状态。
     *
     * @param status 状态值，1=有效，0=无效
     */
    public void setStatus(Integer status) {
        this.status = status;
    }
}
