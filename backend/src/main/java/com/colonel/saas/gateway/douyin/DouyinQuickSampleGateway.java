package com.colonel.saas.gateway.douyin;

import java.util.Map;

public interface DouyinQuickSampleGateway {

    QuickSampleApplyResult apply(QuickSampleApplyCommand command);

    boolean isSupported();

    SupportStatus supportStatus();

    enum SupportStatus {
        REAL_CONNECTED,
        UNSUPPORTED_BY_SDK,
        NOT_AUTHORIZED,
        MOCK_ONLY,
        DISABLED
    }

    record QuickSampleApplyCommand(
            String relationId,
            String productId,
            String activityId,
            String talentId,
            String skuId,
            String spec,
            int quantity,
            String receiverName,
            String receiverPhone,
            String receiverAddress,
            String remark,
            String channelUserId) {
    }

    record QuickSampleApplyResult(
            boolean success,
            String externalApplyId,
            String externalStatus,
            String errorCode,
            String errorMessage,
            Map<String, Object> rawPayload) {
    }
}
