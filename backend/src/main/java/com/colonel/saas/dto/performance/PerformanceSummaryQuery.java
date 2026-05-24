package com.colonel.saas.dto.performance;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class PerformanceSummaryQuery {
    private String timeFilterType;
    private LocalDateTime timeStart;
    private LocalDateTime timeEnd;
    private UUID channelId;
    private UUID recruiterId;
    private String activityId;
    private String productId;
    private String orderStatus;
    private Long partnerId;
    private UUID talentId;
}
