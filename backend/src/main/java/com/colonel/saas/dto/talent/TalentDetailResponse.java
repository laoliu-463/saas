package com.colonel.saas.dto.talent;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TalentDetailResponse {

    private TalentInfo talent;
    private ClaimInfo claim;
    private List<SampleItem> samples;
    private List<OrderItem> orders;

    @Data
    public static class TalentInfo {
        private String id;
        private String nickname;
        private String douyinUid;
        private String douyinNo;
        private String uid;
        private String profileUrl;
        private Long fansCount;
        private Long likesCount;
        private Long worksCount;
        private String ipLocation;
        private String level;
        private Long monthlySales;
        private String mainCategory;
        private String liveSalesBand;
        private String liveViewBand;
        private String liveGpmBand;
        private String videoSalesBand;
        private String videoPlayBand;
        private String videoGpmBand;
        private Boolean blacklisted;
        private String blacklistReason;
        private Long orderCount;
        private Long sampleCount;
        private Long serviceFeeContribution;
        private String contactPhone;
        private String remark;
        private String avatarUrl;
        private List<String> tags;
        private String tagUpdatedBy;
        private String shippingRecipientName;
        private String shippingRecipientPhone;
        private String shippingRecipientAddress;
    }

    @Data
    public static class ClaimInfo {
        private String poolStatus;
        private String ownerId;
        private String ownerName;
        private LocalDateTime claimedAt;
        private LocalDateTime protectedUntil;
        private Integer activeClaimCount;
        private List<ClaimOwnerItem> activeClaimOwners;
        private String recipientName;
        private String recipientPhone;
        private String recipientAddress;
    }

    @Data
    public static class ClaimOwnerItem {
        private String userId;
        private String ownerName;
        private LocalDateTime claimedAt;
        private LocalDateTime protectedUntil;
    }

    @Data
    public static class SampleItem {
        private String sampleRequestId;
        private String productName;
        private String status;
        private String statusText;
        private LocalDateTime createTime;
        private LocalDateTime completeTime;
    }

    @Data
    public static class OrderItem {
        private String orderId;
        private String productName;
        private Long orderAmount;
        private Long serviceFee;
        private String channelName;
        private LocalDateTime createTime;
    }
}
