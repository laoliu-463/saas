package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.entity.ColonelPartner;
import com.colonel.saas.domain.product.event.ProductDomainEventPublisher;
import com.colonel.saas.mapper.ColonelPartnerMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 团长主数据同步服务。
 *
 * <p>职责：从活动表（colonel_activity）、订单表（colonelsettlement_order）、
 * 转链映射表（pick_source_mapping）三个数据源聚合团长信息，
 * 通过 upsert 方式维护团长主数据表（colonel_partner）的最新状态。
 *
 * <p>同步策略：
 * <ul>
 *   <li>按 colonel_buyin_id 为唯一键做合并（merge），取最新名称和更新时间</li>
 *   <li>已有记录仅在新数据更新时间更晚时覆盖名称</li>
 *   <li>人工维护的联系方式字段（manualContact*）不会被自动同步覆盖</li>
 * </ul>
 *
 * <p>依赖服务/仓储：
 * <ul>
 *   <li>{@link ColonelPartnerMapper} —— 团长主数据数据访问</li>
 *   <li>{@link JdbcTemplate} —— 原始 SQL 查询（跨表聚合）</li>
 *   <li>{@link ProductDomainEventPublisher} —— 同步完成事件发布</li>
 * </ul>
 */
@Slf4j
@Service
public class ColonelPartnerSyncService {

    private final ColonelPartnerMapper colonelPartnerMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ProductDomainEventPublisher productDomainEventPublisher;

    public ColonelPartnerSyncService(
            ColonelPartnerMapper colonelPartnerMapper,
            JdbcTemplate jdbcTemplate,
            ProductDomainEventPublisher productDomainEventPublisher) {
        this.colonelPartnerMapper = colonelPartnerMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.productDomainEventPublisher = productDomainEventPublisher;
    }

    /**
     * 全量同步所有团长主数据。
     *
     * <p>执行步骤：
     * <ol>
     *   <li>从活动表加载团长种子数据</li>
     *   <li>从订单表加载团长种子数据</li>
     *   <li>从转链映射表加载团长种子数据</li>
     *   <li>按 colonel_buyin_id 合并所有种子数据</li>
     *   <li>逐条 upsert 到团长主数据表</li>
     *   <li>发布同步完成事件</li>
     * </ol>
     *
     * @return 实际更新/插入的团长数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int syncAll() {
        Map<String, PartnerSeed> seeds = new LinkedHashMap<>();
        loadFromActivities(seeds);
        loadFromOrders(seeds);
        loadFromPickSourceMapping(seeds);
        int upserted = 0;
        for (PartnerSeed seed : seeds.values()) {
            if (upsertSeed(seed)) {
                upserted++;
            }
        }
        productDomainEventPublisher.publishPartnerSyncCompleted(upserted);
        log.info("Colonel partner sync completed, upserted={}", upserted);
        return upserted;
    }

    /**
     * 按名称关键字模糊搜索团长列表。
     *
     * @param keyword 搜索关键字（模糊匹配 colonelName）
     * @param limit   返回数量上限（1~200）
     * @return 匹配的团长列表，按名称升序排列
     */
    public List<ColonelPartner> listByNameKeyword(String keyword, int limit) {
        LambdaQueryWrapper<ColonelPartner> wrapper = new LambdaQueryWrapper<ColonelPartner>()
                .orderByAsc(ColonelPartner::getColonelName)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 200));
        if (StringUtils.hasText(keyword)) {
            wrapper.like(ColonelPartner::getColonelName, keyword.trim());
        }
        return colonelPartnerMapper.selectList(wrapper);
    }

    /**
     * 根据团长名称解析其关联的商品ID集合。
     * 通过名称模糊匹配团长，再从 pick_source_mapping 中查找该团长关联的所有商品。
     *
     * @param colonelName 团长名称（模糊匹配）
     * @return 关联的商品ID集合，未找到则返回空集合
     */
    public Set<String> resolveProductIdsByColonelName(String colonelName) {
        if (!StringUtils.hasText(colonelName)) {
            return Set.of();
        }
        List<ColonelPartner> partners = colonelPartnerMapper.selectList(new LambdaQueryWrapper<ColonelPartner>()
                .like(ColonelPartner::getColonelName, colonelName.trim()));
        if (partners.isEmpty()) {
            return Set.of();
        }
        Set<String> productIds = new LinkedHashSet<>();
        for (ColonelPartner partner : partners) {
            if (!StringUtils.hasText(partner.getColonelBuyinId())) {
                continue;
            }
            jdbcTemplate.query("""
                            SELECT DISTINCT product_id
                            FROM pick_source_mapping
                            WHERE deleted = 0
                              AND product_id IS NOT NULL
                              AND colonel_buyin_id = ?
                            """,
                    rs -> {
                        while (rs.next()) {
                            productIds.add(rs.getString(1));
                        }
                        return null;
                    },
                    partner.getColonelBuyinId());
        }
        return productIds;
    }

    /**
     * 从活动表加载团长种子数据。
     * 优先使用 activity_name 作为团长名称，回退到 shop_name。
     */
    private void loadFromActivities(Map<String, PartnerSeed> seeds) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT
                    CAST(colonel_buyin_id AS TEXT) AS colonel_buyin_id,
                    COALESCE(activity_name, shop_name, CAST(colonel_buyin_id AS TEXT)) AS colonel_name,
                    COALESCE(last_sync_at, update_time, create_time) AS source_updated_at
                FROM colonel_activity
                WHERE deleted = 0
                  AND colonel_buyin_id IS NOT NULL
                """);
        for (Map<String, Object> row : rows) {
            mergeSeed(seeds, row);
        }
    }

    /**
     * 从订单表加载团长种子数据。
     * 使用主团长或副团长的 buyin_id，优先使用 colonel_user_name 作为名称。
     */
    private void loadFromOrders(Map<String, PartnerSeed> seeds) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT
                    CAST(COALESCE(colonel_buyin_id, second_colonel_buyin_id) AS TEXT) AS colonel_buyin_id,
                    COALESCE(colonel_user_name, colonel_nickname, CAST(COALESCE(colonel_buyin_id, second_colonel_buyin_id) AS TEXT)) AS colonel_name,
                    COALESCE(update_time, create_time) AS source_updated_at
                FROM colonelsettlement_order
                WHERE deleted = 0
                  AND COALESCE(colonel_buyin_id, second_colonel_buyin_id) IS NOT NULL
                """);
        for (Map<String, Object> row : rows) {
            mergeSeed(seeds, row);
        }
    }

    /**
     * 从转链映射表加载团长种子数据。
     * 直接使用 colonel_name 字段作为团长名称。
     */
    private void loadFromPickSourceMapping(Map<String, PartnerSeed> seeds) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT
                    colonel_buyin_id,
                    COALESCE(colonel_name, colonel_buyin_id) AS colonel_name,
                    COALESCE(update_time, create_time) AS source_updated_at
                FROM pick_source_mapping
                WHERE deleted = 0
                  AND colonel_buyin_id IS NOT NULL
                """);
        for (Map<String, Object> row : rows) {
            mergeSeed(seeds, row);
        }
    }

    /**
     * 将一行查询结果合并到种子 Map 中。
     * 同一 colonel_buyin_id 的多条记录会合并，取最新名称和最大更新时间。
     *
     * @param seeds 已有的种子数据 Map（key = colonel_buyin_id）
     * @param row   查询结果行
     */
    private void mergeSeed(Map<String, PartnerSeed> seeds, Map<String, Object> row) {
        String buyinId = Objects.toString(row.get("colonel_buyin_id"), "").trim();
        if (!StringUtils.hasText(buyinId)) {
            return;
        }
        String name = Objects.toString(row.get("colonel_name"), "").trim();
        LocalDateTime sourceUpdatedAt = toLocalDateTime(row.get("source_updated_at"));
        PartnerSeed existing = seeds.get(buyinId);
        if (existing == null) {
            seeds.put(buyinId, new PartnerSeed(buyinId, name, sourceUpdatedAt));
            return;
        }
        if (StringUtils.hasText(name) && (!StringUtils.hasText(existing.colonelName()) || isNewer(sourceUpdatedAt, existing.sourceUpdatedAt()))) {
            seeds.put(buyinId, new PartnerSeed(buyinId, name, maxTime(sourceUpdatedAt, existing.sourceUpdatedAt())));
        }
    }

    boolean upsertSeed(ColonelPartner seed) {
        if (seed == null) {
            return false;
        }
        return upsertSeed(new PartnerSeed(
                seed.getColonelBuyinId(),
                seed.getColonelName(),
                seed.getSourceUpdatedAt(),
                seed.getSource()));
    }

    /**
     * 将种子数据 upsert 到团长主数据表。
     * 若记录不存在则创建；若已存在则仅在数据更新时修改，并保护人工维护的联系方式字段。
     *
     * @param seed 团长种子数据
     * @return true 表示有数据变更（insert 或 update）
     */
    private boolean upsertSeed(PartnerSeed seed) {
        ColonelPartner existing = colonelPartnerMapper.selectOne(new LambdaQueryWrapper<ColonelPartner>()
                .eq(ColonelPartner::getColonelBuyinId, seed.colonelBuyinId())
                .last("LIMIT 1"));
        if (existing == null) {
            ColonelPartner created = new ColonelPartner();
            created.setId(UUID.randomUUID());
            created.setColonelBuyinId(seed.colonelBuyinId());
            created.setColonelName(seed.colonelName());
            created.setSource(seed.source());
            created.setSourceUpdatedAt(seed.sourceUpdatedAt());
            created.setFirstSeenAt(LocalDateTime.now());
            created.setLastSyncAt(LocalDateTime.now());
            colonelPartnerMapper.insert(created);
            return true;
        }
        boolean changed = false;
        if (!blankOrNull(seed.colonelName()) && !seed.colonelName().equals(existing.getColonelName())) {
            existing.setColonelName(seed.colonelName());
            changed = true;
        }
        if (seed.sourceUpdatedAt() != null
                && (existing.getSourceUpdatedAt() == null || seed.sourceUpdatedAt().isAfter(existing.getSourceUpdatedAt()))) {
            existing.setSourceUpdatedAt(seed.sourceUpdatedAt());
            changed = true;
        }
        if (StringUtils.hasText(seed.source()) && !seed.source().equals(existing.getSource())) {
            existing.setSource(seed.source());
            changed = true;
        }
        existing.setLastSyncAt(LocalDateTime.now());
        changed = true;
        protectManualContactFields(existing, seed);
        if (changed) {
            colonelPartnerMapper.updateById(existing);
        }
        return changed;
    }

    private boolean blankOrNull(String value) {
        return !StringUtils.hasText(value);
    }

    private boolean isNewer(LocalDateTime candidate, LocalDateTime baseline) {
        return candidate != null && (baseline == null || candidate.isAfter(baseline));
    }

    private LocalDateTime maxTime(LocalDateTime left, LocalDateTime right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }

    private void protectManualContactFields(ColonelPartner existing, PartnerSeed seed) {
        // 自动同步不得用空值覆盖人工维护的联系方式；当前 seed 不含联系方式字段，仅保留占位。
    }

    /**
     * 团长种子数据记录。
     * 用于在同步过程中聚合多个数据源的团长信息。
     *
     * @param colonelBuyinId  团长百应ID（唯一键）
     * @param colonelName     团长名称
     * @param sourceUpdatedAt 数据源最后更新时间
     * @param source          数据来源标识
     */
    private record PartnerSeed(String colonelBuyinId, String colonelName, LocalDateTime sourceUpdatedAt, String source) {
        PartnerSeed(String colonelBuyinId, String colonelName, LocalDateTime sourceUpdatedAt) {
            this(colonelBuyinId, colonelName, sourceUpdatedAt, null);
        }
    }
}
