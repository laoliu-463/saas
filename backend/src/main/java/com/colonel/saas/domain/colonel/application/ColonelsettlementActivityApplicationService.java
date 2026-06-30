package com.colonel.saas.domain.colonel.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.entity.ColonelsettlementActivity;
import com.colonel.saas.gateway.douyin.DouyinActivityGateway;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import com.colonel.saas.service.activity.ActivityAssignmentListSupport;
import com.colonel.saas.service.activity.ActivityPromotionSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 活动列表 Application Service（DDD-COLONEL-002 Slice 2）。
 *
 * <p>职责：提供活动数据的分页查询能力，并支持在数据库为空时自动播种演示数据。
 * 演示数据播种仅在配置 {@code app.activities.seed-demo-on-empty=true} 且本地活动表为空时触发。</p>
 *
 * <p>依赖：
 * <ul>
 *   <li>{@link ColonelsettlementActivityMapper} —— 活动数据访问</li>
 *   <li>{@link DouyinActivityGateway} —— 抖店活动网关</li>
 * </ul>
 */
@Slf4j
@Service
public class ColonelsettlementActivityApplicationService {

    private final ColonelsettlementActivityMapper activityMapper;
    private final DouyinActivityGateway douyinActivityGateway;
    private final boolean seedDemoActivities;

    public ColonelsettlementActivityApplicationService(
            ColonelsettlementActivityMapper activityMapper,
            DouyinActivityGateway douyinActivityGateway,
            @Value("${app.activities.seed-demo-on-empty:false}") boolean seedDemoActivities) {
        this.activityMapper = activityMapper;
        this.douyinActivityGateway = douyinActivityGateway;
        this.seedDemoActivities = seedDemoActivities;
    }

    /**
     * 分页查询活动列表。
     */
    @Transactional(rollbackFor = Exception.class)
    public IPage<ColonelsettlementActivity> getPage(long page, long size, Integer status) {
        long current = Math.max(page, 1);
        long pageSize = Math.max(size, 1);
        ensureSeedData();
        LocalDateTime now = LocalDateTime.now();
        long offset = (current - 1) * pageSize;
        List<ColonelsettlementActivity> records = activityMapper.selectPage(offset, pageSize, status, now);
        long total = activityMapper.countPage(status, now);

        Page<ColonelsettlementActivity> result = new Page<>(current, pageSize);
        result.setTotal(total);
        result.setRecords(records);
        return result;
    }

    /**
     * 从抖店活动列表回写活动状态码/文案到本地库（商品全量同步前刷新活动状态码）。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean syncActivitySummaryFromUpstream(String activityId, String appId) {
        if (!StringUtils.hasText(activityId)) {
            return false;
        }
        String normalizedId = activityId.trim();
        try {
            DouyinActivityGateway.ActivityListResult result = douyinActivityGateway.listActivities(
                    new DouyinActivityGateway.ActivityListQuery(appId, 0, 0L, 1L, 1L, 20L, normalizedId));
            if (result.activityList() == null || result.activityList().isEmpty()) {
                return false;
            }
            for (DouyinActivityGateway.ActivityItem item : result.activityList()) {
                if (item == null || item.activityId() <= 0L) {
                    continue;
                }
                if (!normalizedId.equals(String.valueOf(item.activityId()))) {
                    continue;
                }
                syncFromGatewayItem(item);
                return true;
            }
        } catch (Exception ex) {
            log.warn("Sync activity summary before product refresh failed, activityId={}", normalizedId, ex);
        }
        return false;
    }

    /**
     * 将抖店活动列表条目落库（状态码/文案、时间窗口）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncFromGatewayItem(DouyinActivityGateway.ActivityItem item) {
        if (item == null || item.activityId() <= 0L) {
            return;
        }
        String activityId = String.valueOf(item.activityId());
        LocalDateTime now = LocalDateTime.now();
        activityMapper.upsertListActivitySummary(
                UUID.nameUUIDFromBytes(("list-activity-" + activityId).getBytes(StandardCharsets.UTF_8)),
                activityId,
                item.activityName(),
                item.colonelBuyinId() > 0L ? item.colonelBuyinId() : null,
                parseDateTime(item.activityStartTime()),
                parseDateTime(item.activityEndTime()),
                item.status(),
                item.statusText(),
                now
        );
    }

    public ColonelsettlementActivity findByActivityId(String activityId) {
        if (!StringUtils.hasText(activityId)) {
            return null;
        }
        return activityMapper.selectByActivityId(activityId.trim());
    }

    /**
     * 批量读取活动级招商组长分配摘要。
     */
    public Map<String, ColonelsettlementActivity> findAssignmentsByActivityIds(List<String> activityIds) {
        if (activityIds == null || activityIds.isEmpty()) {
            return Map.of();
        }
        List<String> normalized = activityIds.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            return Map.of();
        }
        return activityMapper.selectAssignmentByActivityIds(normalized).stream()
                .filter(row -> StringUtils.hasText(row.getActivityId()))
                .collect(Collectors.toMap(
                        ColonelsettlementActivity::getActivityId,
                        row -> row,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    public boolean isPromotingActivity(String activityId) {
        if (!StringUtils.hasText(activityId)) {
            return false;
        }
        ColonelsettlementActivity activity = activityMapper.selectByActivityId(activityId.trim());
        return ActivityPromotionSupport.isPromoting(activity);
    }

    /**
     * 按分配筛选从本地库分页查询活动列表。
     */
    public Map<String, Object> buildAssignmentListPage(
            long page,
            long pageSize,
            Integer activityStatusCode,
            String assignmentFilter,
            UUID recruiterUserId,
            String activityKeyword,
            Function<UUID, String> userNameResolver) {
        long current = Math.max(page, 1);
        long size = Math.max(pageSize, 1);
        Integer statusFilter = activityStatusCode != null && activityStatusCode > 0 ? activityStatusCode : null;
        String keyword = StringUtils.hasText(activityKeyword) ? activityKeyword.trim() : null;
        String filter = StringUtils.hasText(assignmentFilter) ? assignmentFilter.trim().toLowerCase() : "mine";
        long offset = (current - 1) * size;
        long total = activityMapper.countPageByAssignment(statusFilter, filter, recruiterUserId, keyword);
        List<ColonelsettlementActivity> records = activityMapper.selectPageByAssignment(
                offset, size, statusFilter, filter, recruiterUserId, keyword);
        return ActivityAssignmentListSupport.buildListPayload(records, total, userNameResolver);
    }

    LocalDateTime parseDateTime(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String text = raw.trim();
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ISO_LOCAL_DATE);
        for (DateTimeFormatter formatter : formatters) {
            try {
                if (formatter == DateTimeFormatter.ISO_LOCAL_DATE) {
                    return java.time.LocalDate.parse(text, formatter).atStartOfDay();
                }
                return LocalDateTime.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
                // try next pattern
            }
        }
        if (text.length() >= 10) {
            try {
                return java.time.LocalDate.parse(text.substring(0, 10)).atStartOfDay();
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
        return null;
    }

    void ensureSeedData() {
        if (!seedDemoActivities) {
            return;
        }
        if (activityMapper.countLocalActivities() > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (int i = 1; i <= 5; i++) {
            LocalDateTime startTime = now.minusDays(i * 2L);
            LocalDateTime endTime = now.plusDays(15L - i);
            activityMapper.insertSeedActivity(
                    UUID.nameUUIDFromBytes(("activity-" + i).getBytes()),
                    "LOCAL_ACTIVITY_" + i,
                    "主链路演示活动-" + i,
                    startTime,
                    endTime,
                    i % 2 == 0 ? "已结束" : "进行中",
                    startTime
            );
        }
    }
}