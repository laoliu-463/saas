package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.base.VersionedEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 商品运营状态实体类，映射数据库 {@code product_operation_state} 表。
 * <p>
 * 该实体属于商品域，记录商品在运营流程中的状态快照，
 * 包括审核、推广、选品入库、置顶、展示控制等运营维度的信息。
 * 与 {@link Product} 通过 {@code product_id} 关联，一条运营状态记录对应一个商品。
 * </p>
 * <p>
 * 继承 {@link VersionedEntity}，支持乐观锁控制，防止并发更新冲突。
 * </p>
 *
 * @see Product 商品基础信息
 * @see com.colonel.saas.common.base.VersionedEntity 乐观锁基类
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_operation_state")
public class ProductOperationState extends VersionedEntity {

    /** 活动 ID，标识商品当前关联的运营活动 */
    @TableField("activity_id")
    private String activityId;

    /** 商品 ID，关联 product 表主键 */
    @TableField("product_id")
    private String productId;

    /** 已绑定的活动 ID，记录商品在精选联盟中绑定的活动 */
    @TableField("bound_activity_id")
    private String boundActivityId;

    /** 业务状态，标识商品在运营流程中的当前阶段（如 active / inactive / pending 等） */
    @TableField("biz_status")
    private String bizStatus;

    /** 指派运营人员的用户 ID */
    @TableField("assignee_id")
    private UUID assigneeId;

    /**
     * 审核状态码。
     * 例如：0=待审核、1=审核通过、2=审核拒绝。
     */
    @TableField("audit_status")
    private Integer auditStatus;

    /** 审核备注，记录审核人填写的审核意见 */
    @TableField("audit_remark")
    private String auditRemark;

    /** 审核附加数据，JSON 格式存储审核过程中的扩展信息 */
    @TableField("audit_payload")
    private String auditPayload;

    /** 推广链接（转链后生成的达人专属链接） */
    @TableField("promote_link")
    private String promoteLink;

    /** 短链接，推广链接的缩短版本，便于分享 */
    @TableField("short_link")
    private String shortLink;

    /** 推广场景类型（如短视频、直播、图文等） */
    @TableField("promotion_scene")
    private Integer promotionScene;

    /** 外部唯一标识，用于与抖店等外部系统做幂等关联 */
    @TableField("external_unique_id")
    private String externalUniqueId;

    /** 是否已被选入商品库 */
    @TableField("selected_to_library")
    private Boolean selectedToLibrary;

    /** 选入商品库的时间 */
    @TableField("selected_at")
    private LocalDateTime selectedAt;

    /** 执行选品操作的用户 ID */
    @TableField("selected_by")
    private UUID selectedBy;

    /** 最近一次运营操作的时间（如审核、推广等） */
    @TableField("last_operation_at")
    private LocalDateTime lastOperationAt;

    /** 置顶生效时间 */
    @TableField("pinned_at")
    private LocalDateTime pinnedAt;

    /** 置顶到期时间，超过该时间后系统自动取消置顶 */
    @TableField("pinned_until")
    private LocalDateTime pinnedUntil;

    /** 执行置顶操作的用户 ID */
    @TableField("pinned_by")
    private UUID pinnedBy;

    /** 展示状态（如 visible / hidden / forced_visible），控制商品在前端是否可见 */
    @TableField("display_status")
    private String displayStatus;

    /** 首次展示时间，记录商品第一次上架展示的时刻 */
    @TableField("first_displayed_at")
    private LocalDateTime firstDisplayedAt;

    /** 最近一次展示时间，记录商品最近一次变为可见状态的时刻 */
    @TableField("last_displayed_at")
    private LocalDateTime lastDisplayedAt;

    /** 隐藏原因，当商品被隐藏时记录具体原因（如"违规下架""手动隐藏"） */
    @TableField("hidden_reason")
    private String hiddenReason;

    /** 展示原因，记录商品当前展示状态的触发原因 */
    @TableField("display_reason")
    private String displayReason;

    /** 展示规则版本号，标识当前生效的展示规则版本 */
    @TableField("display_rule_version")
    private Integer displayRuleVersion;

    /** 是否强制展示（忽略常规展示规则） */
    @TableField("force_display")
    private Boolean forceDisplay;

    /** 执行强制展示操作的用户 ID */
    @TableField("force_display_by")
    private UUID forceDisplayBy;

    /** 强制展示的原因说明 */
    @TableField("force_display_reason")
    private String forceDisplayReason;

    /** 强制展示到期时间，超过该时间后自动恢复常规展示规则 */
    @TableField("force_display_until")
    private LocalDateTime forceDisplayUntil;

    /** 展示优先级，数值越大优先级越高，用于列表排序 */
    @TableField("display_priority")
    private Integer displayPriority;

    /** 是否被手动禁用，手动禁用后商品不再展示 */
    @TableField("manual_disabled")
    private Boolean manualDisabled;
}
