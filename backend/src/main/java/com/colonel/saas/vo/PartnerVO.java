package com.colonel.saas.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PartnerVO {
    private String partnerId;
    private String partnerName;
    private String partnerType;
    private Long shopId;
    private String shopName;
    private Long productCount;
    private LocalDateTime latestSyncTime;
    private Integer status;
}
