package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.base.VersionedEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 达人认领记录实体。
 * <p>
 * 对应数据库表：{@code talent_claim}，记录业务人员对达人的认领操作。
 * 认领后达人进入该人员的保护期，保护期内其他人员无法再次认领。
 * 认领记录关联 {@link Talent}（达人）、{@link SysUser}（认领人）和 {@link SysDept}（认领人所属部门）。
 * 继承 {@link VersionedEntity}，拥有乐观锁支持。
 * </p>
 *
 * @see Talent 达人实体
 * @see TalentEnrichTask 达人资料补全任务
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("talent_claim")
public class TalentClaim extends VersionedEntity {

    /**
     * 达人 ID
     * <p>对应数据库列：{@code talent_id}，关联 {@link Talent} 实体的主键</p>
     */
    @TableField("talent_id")
    private UUID talentId;

    /**
     * 认领人用户 ID
     * <p>对应数据库列：{@code user_id}，发起认领操作的业务人员 ID，关联 {@link SysUser} 主键</p>
     */
    @TableField("user_id")
    private UUID userId;

    /**
     * 达人抖音 UID
     * <p>对应数据库列：{@code talent_uid}，达人在抖音平台的唯一标识，
     * 冗余存储便于直接关联外部达人数据</p>
     */
    @TableField("talent_uid")
    private String talentUid;

    /**
     * 认领人所属部门 ID
     * <p>对应数据库列：{@code dept_id}，认领操作人所在部门，关联 {@link SysDept} 主键</p>
     */
    @TableField("dept_id")
    private UUID deptId;

    /**
     * 认领类型
     * <p>对应数据库列：{@code claim_type}，标识认领的来源或方式</p>
     */
    @TableField("claim_type")
    private Integer claimType;

    /**
     * 认领时间
     * <p>对应数据库列：{@code apply_time}，业务人员发起认领操作的时间戳</p>
     */
    @TableField("apply_time")
    private LocalDateTime claimedAt;

    /**
     * 保护期截止时间
     * <p>对应数据库列：{@code expire_time}，保护期结束时间。
     * 在此时间之前，其他人员无法认领该达人</p>
     */
    @TableField("expire_time")
    private LocalDateTime protectedUntil;

    /**
     * 寄样收件人姓名
     * <p>对应数据库列：{@code recipient_name}，认领时填写的寄样收件人姓名</p>
     */
    @TableField("recipient_name")
    private String recipientName;

    /**
     * 寄样收件人电话
     * <p>对应数据库列：{@code recipient_phone}，认领时填写的寄样收件人联系电话</p>
     */
    @TableField("recipient_phone")
    private String recipientPhone;

    /**
     * 寄样收件地址
     * <p>对应数据库列：{@code recipient_address}，认领时填写的寄样收件详细地址</p>
     */
    @TableField("recipient_address")
    private String recipientAddress;

    /**
     * 认领状态
     * <p>对应数据库列：{@code status}，记录认领的当前状态</p>
     */
    private Integer status;
}
