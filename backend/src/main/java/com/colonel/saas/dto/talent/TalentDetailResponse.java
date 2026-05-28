package com.colonel.saas.dto.talent;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 达人详情响应 DTO。
 * <p>
 * 返回达人完整详情页数据，包括达人基本信息、归属认领状态、关联寄样记录和关联订单记录。
 * 关联业务领域：达人域（Talent）、寄样域（Sample）、订单域（Order）。
 * </p>
 */
@Data
public class TalentDetailResponse {

    /** 达人基础信息 */
    private TalentInfo talent;
    /** 达人归属认领信息 */
    private ClaimInfo claim;
    /** 关联的寄样记录列表 */
    private List<SampleItem> samples;
    /** 关联的订单记录列表 */
    private List<OrderItem> orders;

    /**
     * 达人基础信息内部类。
     */
    @Data
    public static class TalentInfo {
        /** 达人 ID */
        private String id;
        /** 达人昵称 */
        private String nickname;
        /** 抖音平台 UID */
        private String douyinUid;
        /** 抖音号 */
        private String douyinNo;
        /** 通用 UID */
        private String uid;
        /** 达人主页链接 */
        private String profileUrl;
        /** 粉丝数 */
        private Long fansCount;
        /** 累计获赞数 */
        private Long likesCount;
        /** 作品数 */
        private Long worksCount;
        /** IP 归属地 */
        private String ipLocation;
        /** 达人等级 */
        private String level;
        /** 月销售额（单位：分） */
        private Long monthlySales;
        /** 主推类目 */
        private String mainCategory;
        /** 直播销售额区间 */
        private String liveSalesBand;
        /** 直播观看量区间 */
        private String liveViewBand;
        /** 直播 GPM 区间 */
        private String liveGpmBand;
        /** 视频销售额区间 */
        private String videoSalesBand;
        /** 视频播放量区间 */
        private String videoPlayBand;
        /** 视频 GPM 区间 */
        private String videoGpmBand;
        /** 是否已拉黑 */
        private Boolean blacklisted;
        /** 拉黑原因 */
        private String blacklistReason;
        /** 累计订单数 */
        private Long orderCount;
        /** 累计寄样数 */
        private Long sampleCount;
        /** 服务费贡献总额（单位：分） */
        private Long serviceFeeContribution;
        /** 联系电话 */
        private String contactPhone;
        /** 备注 */
        private String remark;
        /** 头像链接 */
        private String avatarUrl;
        /** 标签列表 */
        private List<String> tags;
        /** 标签最近更新人 */
        private String tagUpdatedBy;
        /** 默认收货人姓名 */
        private String shippingRecipientName;
        /** 默认收货人电话 */
        private String shippingRecipientPhone;
        /** 默认收货地址 */
        private String shippingRecipientAddress;
    }

    /**
     * 达人归属认领信息内部类。
     */
    @Data
    public static class ClaimInfo {
        /** 池状态（如 in_pool、claimed） */
        private String poolStatus;
        /** 当前归属负责人 ID */
        private String ownerId;
        /** 当前归属负责人姓名 */
        private String ownerName;
        /** 认领时间 */
        private LocalDateTime claimedAt;
        /** 保护期截止时间 */
        private LocalDateTime protectedUntil;
        /** 当前有效认领数 */
        private Integer activeClaimCount;
        /** 有效认领的负责人列表 */
        private List<ClaimOwnerItem> activeClaimOwners;
        /** 收货人姓名 */
        private String recipientName;
        /** 收货人电话 */
        private String recipientPhone;
        /** 收货地址 */
        private String recipientAddress;
    }

    /**
     * 认领负责人条目内部类。
     */
    @Data
    public static class ClaimOwnerItem {
        /** 负责人用户 ID */
        private String userId;
        /** 负责人姓名 */
        private String ownerName;
        /** 认领时间 */
        private LocalDateTime claimedAt;
        /** 保护期截止时间 */
        private LocalDateTime protectedUntil;
    }

    /**
     * 关联寄样记录内部类。
     */
    @Data
    public static class SampleItem {
        /** 寄样申请 ID */
        private String sampleRequestId;
        /** 商品名称 */
        private String productName;
        /** 寄样状态编码 */
        private String status;
        /** 寄样状态中文描述 */
        private String statusText;
        /** 创建时间 */
        private LocalDateTime createTime;
        /** 完成时间 */
        private LocalDateTime completeTime;
    }

    /**
     * 关联订单记录内部类。
     */
    @Data
    public static class OrderItem {
        /** 订单 ID */
        private String orderId;
        /** 商品名称 */
        private String productName;
        /** 订单金额（单位：分） */
        private Long orderAmount;
        /** 服务费（单位：分） */
        private Long serviceFee;
        /** 渠道名称 */
        private String channelName;
        /** 创建时间 */
        private LocalDateTime createTime;
    }
}
