package com.colonel.saas.vo.sample;

/**
 * 合作台单个操作的后端可用性。
 */
public record SampleActionAvailabilityVO(
        boolean enabled,
        String disabledReason) {

    public static SampleActionAvailabilityVO available() {
        return new SampleActionAvailabilityVO(true, null);
    }

    public static SampleActionAvailabilityVO unavailable(String reason) {
        return new SampleActionAvailabilityVO(false, reason);
    }
}
