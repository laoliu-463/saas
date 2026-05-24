package com.colonel.saas.service.display;

import java.util.UUID;

public record DisplayRuleOperatorContext(String operatorType, UUID operatorId) {

    public static final String TYPE_SYSTEM = "SYSTEM";
    public static final String TYPE_JOB = "JOB";
    public static final String TYPE_ADMIN = "ADMIN";

    public static DisplayRuleOperatorContext system() {
        return new DisplayRuleOperatorContext(TYPE_SYSTEM, null);
    }

    public static DisplayRuleOperatorContext job() {
        return new DisplayRuleOperatorContext(TYPE_JOB, null);
    }

    public static DisplayRuleOperatorContext admin(UUID adminId) {
        return new DisplayRuleOperatorContext(TYPE_ADMIN, adminId);
    }
}
