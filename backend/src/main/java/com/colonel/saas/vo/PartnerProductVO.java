package com.colonel.saas.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PartnerProductVO {
    private String productId;
    private String productName;
    private String activityId;
    private String cover;
    private String priceText;
    private Long shopId;
    private String shopName;
    private String categoryName;
    private Long sales;
    private Integer status;
    private String statusText;
    private LocalDateTime latestSyncTime;
}
