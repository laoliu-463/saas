package com.colonel.saas.constant;

public final class ProductDomainEventTypes {

    public static final String PRODUCT_LISTED = "ProductListedEvent";
    public static final String PRODUCT_HIDDEN = "ProductHiddenEvent";
    public static final String PRODUCT_OWNER_CHANGED = "ProductOwnerChangedEvent";
    public static final String ACTIVITY_SYNC_COMPLETED = "ActivitySyncCompletedEvent";
    public static final String PARTNER_SYNC_COMPLETED = "PartnerSyncCompletedEvent";
    public static final String ACTIVITY_EXTENDED = "ActivityExtendedEvent";
    public static final String PRODUCT_DISPLAY_RULE_APPLIED = "ProductDisplayRuleAppliedEvent";
    public static final String PRODUCT_FORCE_DISPLAY_CHANGED = "ProductForceDisplayChangedEvent";
    public static final String COLONEL_PARTNER_SYNCED = "ColonelPartnerSyncedEvent";

    private ProductDomainEventTypes() {
    }
}
