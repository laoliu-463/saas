package com.colonel.saas.domain.shared.attribution;

/** 订单渠道、招商归属的稳定来源值。 */
public final class AttributionSource {
    public static final String PICK_SOURCE = "pick_source";
    public static final String NATIVE_UNIQUE_LINK_OWNER = "native_unique_link_owner";
    public static final String ACTIVITY_OWNER = "activity_owner";
    public static final String AMBIGUOUS = "ambiguous";
    public static final String UNATTRIBUTED = "unattributed";

    private AttributionSource() {
    }
}
