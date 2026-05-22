package com.colonel.saas.common.time;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Application-wide timezone aligned with Jackson ({@code Asia/Shanghai}).
 */
public final class AppZone {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private AppZone() {
    }

    public static LocalDateTime fromEpochSecond(long epochSecond) {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSecond), ZONE);
    }

    public static LocalDateTime fromEpochMilli(long epochMilli) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZONE);
    }

    public static long toEpochSecond(LocalDateTime dateTime) {
        if (dateTime == null) {
            return 0L;
        }
        return dateTime.atZone(ZONE).toEpochSecond();
    }
}
