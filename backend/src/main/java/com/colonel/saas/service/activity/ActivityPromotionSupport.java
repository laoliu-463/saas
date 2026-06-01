package com.colonel.saas.service.activity;

import com.colonel.saas.entity.ColonelsettlementActivity;
import org.springframework.util.StringUtils;

import java.util.Map;
/**
 * 抖店活动「推广中」状态判定（以活动维度为准，不用商品联盟 status 代替）。
 */
public final class ActivityPromotionSupport {

    public static final int PROMOTING_STATUS_CODE = 5;

    private ActivityPromotionSupport() {
    }

    /**
     * 有有效状态码时仅以码为准，避免「码=报名中 + 文案=推广中」重叠判定。
     */
    public static boolean isPromoting(Integer activityStatusCode, String activityStatusText) {
        if (activityStatusCode != null && activityStatusCode > 0) {
            return activityStatusCode == PROMOTING_STATUS_CODE;
        }
        return StringUtils.hasText(activityStatusText) && activityStatusText.contains("推广中");
    }

    public static boolean isPromoting(ColonelsettlementActivity activity) {
        if (activity == null) {
            return false;
        }
        return isPromoting(activity.getActivityStatusCode(), activity.getActivityStatusText());
    }

    public static boolean isPromoting(Map<String, Object> activityRow) {
        if (activityRow == null || activityRow.isEmpty()) {
            return false;
        }
        Integer code = readInteger(activityRow.get("activityStatus"));
        if (code == null) {
            code = readInteger(activityRow.get("status"));
        }
        if (code == null) {
            code = readInteger(activityRow.get("activity_status_code"));
        }
        String text = firstNonBlank(
                activityRow.get("statusText"),
                activityRow.get("activity_status_text"),
                activityRow.get("status_text"));
        return isPromoting(code, text == null ? null : String.valueOf(text));
    }

    private static Integer readInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String firstNonBlank(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).trim();
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return null;
    }
}
