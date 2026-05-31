package com.colonel.saas.constant;

/**
 * 寄样单内部状态码（与 sample_request.status、状态日志 from/to 一致）。
 */
public final class SampleStatusCodes {

    public static final int PENDING_AUDIT = 1;
    public static final int PENDING_SHIP = 2;
    public static final int SHIPPING = 3;
    public static final int DELIVERED = 4;
    public static final int PENDING_HOMEWORK = 5;
    public static final int COMPLETED = 6;
    public static final int REJECTED = 7;
    public static final int CLOSED = 8;

    private SampleStatusCodes() {
    }
}
