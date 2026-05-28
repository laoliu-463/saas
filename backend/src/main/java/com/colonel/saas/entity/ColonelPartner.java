package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.base.VersionedEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 团长合作伙伴实体。
 * <p>
 * 对应数据库表：{@code colonel_partner}，记录抖音平台团长（即本系统运营方）的合作伙伴信息。
 * 团长伙伴数据通过百应 API 同步，支持手动维护联系方式。
 * 继承 {@link VersionedEntity}，拥有乐观锁支持。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("colonel_partner")
public class ColonelPartner extends VersionedEntity {

    /**
     * 百应平台团长 ID
     * <p>对应数据库列：{@code colonel_buyin_id}，百应平台分配的团长唯一标识</p>
     */
    @TableField("colonel_buyin_id")
    private String colonelBuyinId;

    /**
     * 团长名称
     * <p>对应数据库列：{@code colonel_name}，团长在百应平台的显示名称</p>
     */
    @TableField("colonel_name")
    private String colonelName;

    /**
     * 联系人姓名
     * <p>对应数据库列：{@code contact_name}，团长的主要联系人姓名</p>
     */
    @TableField("contact_name")
    private String contactName;

    /**
     * 联系电话
     * <p>对应数据库列：{@code contact_phone}，团长的联系电话</p>
     */
    @TableField("contact_phone")
    private String contactPhone;

    /**
     * 头像链接
     * <p>对应数据库列：{@code avatar_url}，团长的头像 CDN URL</p>
     */
    @TableField("avatar_url")
    private String avatarUrl;

    /**
     * 联系微信
     * <p>对应数据库列：{@code contact_wechat}，团长的微信号（业务手动维护）</p>
     */
    @TableField("contact_wechat")
    private String contactWechat;

    /**
     * 联系备注
     * <p>对应数据库列：{@code contact_remark}，关于联系方式的额外备注信息</p>
     */
    @TableField("contact_remark")
    private String contactRemark;

    /**
     * 数据来源
     * <p>对应数据库列：{@code source}，标识数据的获取渠道，
     * 如 "BUYIN"（百应 API 同步）、"MANUAL"（手动录入）等</p>
     */
    private String source;

    /**
     * 首次发现时间
     * <p>对应数据库列：{@code first_seen_at}，系统首次发现该团长的时间</p>
     */
    @TableField("first_seen_at")
    private LocalDateTime firstSeenAt;

    /**
     * 最后同步时间
     * <p>对应数据库列：{@code last_sync_at}，最近一次从百应 API 同步数据的时间</p>
     */
    @TableField("last_sync_at")
    private LocalDateTime lastSyncAt;

    /**
     * 手动联系方式更新时间
     * <p>对应数据库列：{@code manual_contact_updated_at}，业务人员最后一次手动更新联系方式的时间</p>
     */
    @TableField("manual_contact_updated_at")
    private LocalDateTime manualContactUpdatedAt;

    /**
     * 手动联系方式更新人
     * <p>对应数据库列：{@code manual_contact_updated_by}，最后手动更新联系方式的操作人标识</p>
     */
    @TableField("manual_contact_updated_by")
    private String manualContactUpdatedBy;

    /**
     * 原始响应载荷
     * <p>JSON 对象格式，对应数据库列：{@code raw_payload}，
     * 存储百应 API 返回的完整原始数据，用于问题排查和数据回放</p>
     */
    @TableField(value = "raw_payload", typeHandler = com.colonel.saas.common.typehandler.JsonbTypeHandler.class)
    private java.util.Map<String, Object> rawPayload;

    /**
     * 数据源更新时间
     * <p>对应数据库列：{@code source_updated_at}，数据源（如百应平台）中该记录的最后更新时间</p>
     */
    @TableField("source_updated_at")
    private LocalDateTime sourceUpdatedAt;
}
