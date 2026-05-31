package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.entity.ColonelsettlementActivity;
import com.colonel.saas.gateway.douyin.DouyinActivityGateway;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import com.colonel.saas.service.activity.ActivityPromotionSupport;
import com.colonel.saas.service.activity.ActivityAssignmentListSupport;
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
 * 活动列表服务。
 *
 * <p>职责：提供活动数据的分页查询能力，并支持在数据库为空时自动播种演示数据。
 * 演示数据播种仅在配置 {@code app.activities.seed-demo-on-empty=true} 且本地活动表为空时触发。
 *
 * <p>依赖服务/仓储：
 * <ul>
 *   <li>{@link ColonelsettlementActivityMapper} —— 活动数据访问</li>
 * </ul>
 */
@Slf4j
@Service
public class ColonelsettlementActivityService {

    /** 活动数据访问 */
    private final ColonelsettlementActivityMapper activityMapper;
    /** 抖店活动网关（商品同步前刷新活动状态码） */
    private final DouyinActivityGateway douyinActivityGateway;
    /** 商品展示规则（活动状态变更后清理推广中缓存） */
    private final ProductDisplayRuleService productDisplayRuleService;
    /** 商品服务（推广中且已分配招商时批量补齐商品库） */
    private final ProductService productService;
    /** 是否启用演示数据播种（由配置项 app.activities.seed-demo-on-empty 控制） */
    private final boolean seedDemoActivities;

    public ColonelsettlementActivityService(
            ColonelsettlementActivityMapper activityMapper,
            DouyinActivityGateway douyinActivityGateway,
            ProductDisplayRuleService productDisplayRuleService,
            ProductService productService,
            @Value("${app.activities.seed-demo-on-empty:false}") boolean seedDemoActivities) {
        this.activityMapper = activityMapper;
        this.douyinActivityGateway = douyinActivityGateway;
        this.productDisplayRuleService = productDisplayRuleService;
        this.productService = productService;
        this.seedDemoActivities = seedDemoActivities;
    }

    /**
     * 分页查询活动列表。
     *
     * <p>执行逻辑：
     * <ol>
     *   <li>校正分页参数（page 最小为 1，size 最小为 1）</li>
     *   <li>若启用演示数据播种且本地活动表为空，自动插入 5 条演示活动</li>
     *   <li>根据状态和时间范围查询活动列表及总数</li>
     * </ol>
     *
     * @param page   页码（从 1 开始，小于 1 时自动校正为 1）
     * @param size   每页大小（小于 1 时自动校正为 1）
     * @param status 活动状态筛选条件，为 null 时不筛选
     * @return 分页结果，包含活动列表和总数
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
     * 确保本地演示数据已播种。
     * 仅当 {@code seedDemoActivities} 为 true 且本地活动数量为 0 时，插入 5 条演示活动数据。
     * 每条活动的时间范围和状态各不相同，用于开发和演示环境的快速初始化。
     */
    /**
     * 将抖店活动列表条目落库（状态码/文案、时间窗口），供分配与推广中规则读取。
     */
    /**
     * 商品全量同步前，从抖店活动列表回写活动状态码/文案到本地库。
     * <p>避免列表展示为「推广中」但 {@code colonel_activity.activity_status_code} 未更新导致无法自动入商品库。</p>
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
        productDisplayRuleService.clearPromotingActivityCache(activityId);
        ColonelsettlementActivity synced = activityMapper.selectByActivityId(activityId);
        if (ActivityPromotionSupport.shouldForceLibraryDisplay(synced)) {
            productService.ensureAssignedPromotingActivityProductsInLibrary(activityId);
        }
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
     * 按分配筛选从本地库分页查询活动列表，供 assignmentFilter 与招商 self 范围使用。
     *
     * @param page               页码（从 1 开始）
     * @param pageSize           每页条数
     * @param activityStatusCode 抖店活动状态码，0 或 null 表示全部
     * @param assignmentFilter   assigned / unassigned / mine
     * @param recruiterUserId    mine 筛选时的当前用户 ID
     * @param activityKeyword    活动 ID 或名称关键字
     * @param userNameResolver   解析招商用户显示名
     * @return 与上游活动列表兼容的 Map 结构
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

    private LocalDateTime parseDateTime(String raw) {
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

    private void ensureSeedData() {
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

