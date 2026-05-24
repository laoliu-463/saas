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

    public List<ColonelPartner> listByNameKeyword(String keyword, int limit) {
        LambdaQueryWrapper<ColonelPartner> wrapper = new LambdaQueryWrapper<ColonelPartner>()
                .orderByAsc(ColonelPartner::getColonelName)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 200));
        if (StringUtils.hasText(keyword)) {
            wrapper.like(ColonelPartner::getColonelName, keyword.trim());
        }
        return colonelPartnerMapper.selectList(wrapper);
    }

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

    private void loadFromActivities(Map<String, PartnerSeed> seeds) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT
                    CAST(colonel_buyin_id AS TEXT) AS colonel_buyin_id,
                    COALESCE(activity_name, shop_name, CAST(colonel_buyin_id AS TEXT)) AS colonel_name,
                    COALESCE(last_sync_at, updated_at, created_at) AS source_updated_at
                FROM colonel_activity
                WHERE deleted = 0
                  AND colonel_buyin_id IS NOT NULL
                """);
        for (Map<String, Object> row : rows) {
            mergeSeed(seeds, row);
        }
    }

    private void loadFromOrders(Map<String, PartnerSeed> seeds) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT
                    CAST(COALESCE(colonel_buyin_id, second_colonel_buyin_id) AS TEXT) AS colonel_buyin_id,
                    COALESCE(colonel_user_name, colonel_nickname, CAST(COALESCE(colonel_buyin_id, second_colonel_buyin_id) AS TEXT)) AS colonel_name,
                    COALESCE(updated_at, create_time) AS source_updated_at
                FROM colonelsettlement_order
                WHERE deleted = 0
                  AND COALESCE(colonel_buyin_id, second_colonel_buyin_id) IS NOT NULL
                """);
        for (Map<String, Object> row : rows) {
            mergeSeed(seeds, row);
        }
    }

    private void loadFromPickSourceMapping(Map<String, PartnerSeed> seeds) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT
                    colonel_buyin_id,
                    COALESCE(colonel_name, colonel_buyin_id) AS colonel_name,
                    COALESCE(updated_at, created_at) AS source_updated_at
                FROM pick_source_mapping
                WHERE deleted = 0
                  AND colonel_buyin_id IS NOT NULL
                """);
        for (Map<String, Object> row : rows) {
            mergeSeed(seeds, row);
        }
    }

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

    private record PartnerSeed(String colonelBuyinId, String colonelName, LocalDateTime sourceUpdatedAt, String source) {
        PartnerSeed(String colonelBuyinId, String colonelName, LocalDateTime sourceUpdatedAt) {
            this(colonelBuyinId, colonelName, sourceUpdatedAt, null);
        }
    }
}
