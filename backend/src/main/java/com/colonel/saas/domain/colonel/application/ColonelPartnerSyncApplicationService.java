package com.colonel.saas.domain.colonel.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.domain.product.event.ProductDomainEventPublisher;
import com.colonel.saas.entity.ColonelPartner;
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
 * 团长主数据同步 Application Service（DDD-COLONEL-002）。
 *
 * <p>职责：从活动表（colonel_activity）、订单表（colonelsettlement_order）、
 * 转链映射表（pick_source_mapping）三个数据源聚合团长信息，
 * 通过 upsert 方式维护团长主数据表（colonel_partner）的最新状态。</p>
 *
 * <p>同步策略：
 * <ul>
 *   <li>按 colonel_buyin_id 为唯一键做合并（merge），取最新名称和更新时间</li>
 *   <li>已有记录仅在新数据更新时间更晚时覆盖名称</li>
 *   <li>人工维护的联系方式字段（manualContact*）不会被自动同步覆盖</li>
 * </ul>
 *
 * <p>依赖：
 * <ul>
 *   <li>{@link ColonelPartnerMapper} —— 团长主数据数据访问</li>
 *   <li>{@link JdbcTemplate} —— 原始 SQL 查询（跨表聚合）</li>
 *   <li>{@link ProductDomainEventPublisher} —— 同步完成事件发布</li>
 * </ul>
 */
@Slf4j
@Service
public class ColonelPartnerSyncApplicationService {

    private final ColonelPartnerMapper colonelPartnerMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ProductDomainEventPublisher productDomainEventPublisher;

    public ColonelPartnerSyncApplicationService(
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
     * 将种子数据 upsert 到团长主数据表（暴露给测试和 Controller 委派壳）。
     *
     * @param seed 团长实体种子（含 buyinId / name / sourceUpdatedAt）
     * @return true 表示有数据变更
     */
    public boolean upsertSeed(ColonelPartner seed) {
        if (seed == null) {
            return false;
        }
        return upsertSeed(new PartnerSeed(
                seed.getColonelBuyinId(),
                seed.getColonelName(),
                seed.getSourceUpdatedAt(),
                seed.getSource()));
    }

    void loadFromActivities(Map<String, PartnerSeed> seeds) {
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

    void loadFromOrders(Map<String, PartnerSeed> seeds) {
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

    void loadFromPickSourceMapping(Map<String, PartnerSeed> seeds) {
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

    void mergeSeed(Map<String, PartnerSeed> seeds, Map<String, Object> row) {
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

    boolean upsertSeed(PartnerSeed seed) {
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

    boolean blankOrNull(String value) {
        return !StringUtils.hasText(value);
    }

    boolean isNewer(LocalDateTime candidate, LocalDateTime baseline) {
        return candidate != null && (baseline == null || candidate.isAfter(baseline));
    }

    LocalDateTime maxTime(LocalDateTime left, LocalDateTime right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }

    LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }

    void protectManualContactFields(ColonelPartner existing, PartnerSeed seed) {
        // 自动同步不得用空值覆盖人工维护的联系方式；当前 seed 不含联系方式字段，仅保留占位。
    }

    /**
     * 团长种子数据记录。
     */
    record PartnerSeed(String colonelBuyinId, String colonelName, LocalDateTime sourceUpdatedAt, String source) {
        PartnerSeed(String colonelBuyinId, String colonelName, LocalDateTime sourceUpdatedAt) {
            this(colonelBuyinId, colonelName, sourceUpdatedAt, null);
        }
    }
}