package com.colonel.saas.service.activity;

import com.colonel.saas.domain.ActivityStatusResolver;
import com.colonel.saas.entity.ColonelsettlementActivity;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * 将本地 colonel_activity 行映射为活动列表 API 条目。
 */
public final class ActivityAssignmentListSupport {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private ActivityAssignmentListSupport() {
    }

    public static Map<String, Object> buildListPayload(
            List<ColonelsettlementActivity> records,
            long total,
            Function<UUID, String> userNameResolver) {
        List<Map<String, Object>> activityList = new ArrayList<>();
        for (ColonelsettlementActivity row : records) {
            activityList.add(toListItem(row, userNameResolver));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("activityList", activityList);
        payload.put("total", total);
        return payload;
    }

    public static Map<String, Object> toListItem(
            ColonelsettlementActivity row,
            Function<UUID, String> userNameResolver) {
        Map<String, Object> item = new LinkedHashMap<>();
        String activityId = row.getActivityId();
        item.put("activityId", parseActivityIdValue(activityId));
        item.put("activityName", row.getName());
        Integer statusCode = row.getActivityStatusCode();
        if (statusCode != null) {
            item.put("status", statusCode);
            item.put("activityStatus", statusCode);
            // 根据状态码实时生成状态文本，避免数据库中的旧文本导致前后端不一致
            item.put("statusText", ActivityStatusResolver.toText(statusCode));
        }
        String start = formatDate(row.getStartTime());
        String end = formatDate(row.getEndTime());
        if (start != null) {
            item.put("activityStartTime", start);
            item.put("startTime", start);
            item.put("applicationStartTime", start);
        }
        if (end != null) {
            item.put("activityEndTime", end);
            item.put("endTime", end);
            item.put("applicationEndTime", end);
        }
        if (row.getColonelBuyinId() != null) {
            item.put("colonelBuyinId", row.getColonelBuyinId());
        }
        if (row.getCommissionRate() != null) {
            item.put("commissionRate", row.getCommissionRate());
        }
        if (row.getServiceRate() != null) {
            item.put("serviceRate", row.getServiceRate());
        }
        if (row.getLastSyncAt() != null) {
            item.put("lastSyncAt", row.getLastSyncAt());
        }
        if (row.getRecruiterUserId() != null) {
            String assigneeName = userNameResolver.apply(row.getRecruiterUserId());
            item.put("activityAssigneeId", row.getRecruiterUserId());
            item.put("activityAssigneeName", assigneeName);
            item.put("assigneeId", row.getRecruiterUserId());
            item.put("assigneeName", assigneeName);
            item.put("recruiterName", assigneeName);
            item.put("recruiterUserId", row.getRecruiterUserId());
            item.put("recruiterUserName", assigneeName);
        }
        if (row.getRecruiterDeptId() != null) {
            item.put("recruiterDeptId", row.getRecruiterDeptId());
        }
        if (row.getAssignedAt() != null) {
            item.put("assignedAt", row.getAssignedAt());
        }
        if (row.getAssignedBy() != null) {
            item.put("assignedBy", row.getAssignedBy());
        }
        return item;
    }

    private static Object parseActivityIdValue(String activityId) {
        if (!StringUtils.hasText(activityId)) {
            return activityId;
        }
        try {
            return Long.parseLong(activityId.trim());
        } catch (NumberFormatException ignored) {
            return activityId.trim();
        }
    }

    private static String formatDate(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.format(DATE_FORMAT);
    }
}
