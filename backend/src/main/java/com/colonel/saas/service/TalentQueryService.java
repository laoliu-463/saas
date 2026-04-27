package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.dto.talent.TalentDetailResponse;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.entity.TalentClaim;
import com.colonel.saas.entity.TalentEnrichTask;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.mapper.SampleRequestMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.mapper.TalentClaimMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TalentQueryService {

    private static final int CLAIM_STATUS_ACTIVE = 1;
    private static final int CLAIM_STATUS_EXPIRED = 2;

    private final TalentService talentService;
    private final TalentClaimMapper talentClaimMapper;
    private final SysUserMapper sysUserMapper;
    private final SampleRequestMapper sampleRequestMapper;
    private final JdbcTemplate jdbcTemplate;

    public TalentQueryService(
            TalentService talentService,
            TalentClaimMapper talentClaimMapper,
            SysUserMapper sysUserMapper,
            SampleRequestMapper sampleRequestMapper,
            JdbcTemplate jdbcTemplate) {
        this.talentService = talentService;
        this.talentClaimMapper = talentClaimMapper;
        this.sysUserMapper = sysUserMapper;
        this.sampleRequestMapper = sampleRequestMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    public IPage<Talent> page(long page,
                              long size,
                              String keyword,
                              String region,
                              String poolStatus,
                              String ownerKeyword,
                              Long minFans,
                              Long maxFans,
                              DataScope dataScope,
                              UUID userId,
                              UUID deptId) {
        if (StringUtils.hasText(poolStatus) || StringUtils.hasText(ownerKeyword)) {
            return pageWithClaimFilters(page, size, keyword, region, poolStatus, ownerKeyword, minFans, maxFans, dataScope, userId, deptId);
        }
        IPage<Talent> basePage = talentService.page(page, size, keyword, region, minFans, maxFans, dataScope, userId, deptId);
        List<Talent> records = basePage.getRecords();
        enrichTalentCards(records);
        Page<Talent> result = new Page<>(basePage.getCurrent(), basePage.getSize(), basePage.getTotal());
        result.setRecords(records);
        return result;
    }

    private IPage<Talent> pageWithClaimFilters(long page,
                                               long size,
                                               String keyword,
                                               String region,
                                               String poolStatus,
                                               String ownerKeyword,
                                               Long minFans,
                                               Long maxFans,
                                               DataScope dataScope,
                                               UUID userId,
                                               UUID deptId) {
        IPage<Talent> fullPage = talentService.page(1, 1000, keyword, region, minFans, maxFans, dataScope, userId, deptId);
        List<Talent> records = new ArrayList<>(fullPage.getRecords());
        enrichTalentCards(records);

        List<Talent> filtered = records.stream()
                .filter(talent -> matchesPoolStatus(talent, poolStatus))
                .filter(talent -> matchesOwnerKeyword(talent, ownerKeyword))
                .toList();

        int fromIndex = (int) Math.max(0, (page - 1) * size);
        int toIndex = (int) Math.min(filtered.size(), fromIndex + size);
        List<Talent> pageRecords = fromIndex >= filtered.size() ? List.of() : filtered.subList(fromIndex, toIndex);

        Page<Talent> result = new Page<>(page, size, filtered.size());
        result.setRecords(pageRecords);
        return result;
    }

    public TalentDetailResponse detail(UUID talentId) {
        Talent talent = talentService.getById(talentId);
        enrichTalentCards(List.of(talent));

        TalentDetailResponse response = new TalentDetailResponse();
        response.setTalent(toTalentInfo(talent));
        response.setClaim(toClaimInfo(talent));
        response.setSamples(loadSamples(talent));
        response.setOrders(loadOrders(talent));
        return response;
    }

    private void enrichTalentCards(List<Talent> talents) {
        if (talents == null || talents.isEmpty()) {
            return;
        }

        Set<UUID> talentIds = talents.stream().map(Talent::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        ClaimMaps claimMaps = loadClaimMaps(talentIds);
        Map<UUID, SysUser> ownerMap = loadOwnerMap(claimMaps.allClaims());
        Map<UUID, Long> sampleCountMap = loadSampleCounts(
                talentIds
        );
        Map<String, OrderAggregate> orderAggregateMap = loadOrderAggregates(
                talents.stream().map(Talent::getDouyinUid).filter(StringUtils::hasText).collect(Collectors.toSet())
        );

        for (Talent talent : talents) {
            TalentClaim claim = claimMaps.activeClaims().get(talent.getId());
            if (claim != null) {
                talent.setPoolStatus("PRIVATE");
                talent.setOwnerId(claim.getUserId());
                talent.setClaimedAt(claim.getClaimedAt());
                talent.setProtectedUntil(claim.getProtectedUntil());
                SysUser owner = ownerMap.get(claim.getUserId());
                talent.setOwnerName(owner == null ? null : displayName(owner));
            } else {
                talent.setPoolStatus("PUBLIC");
                talent.setOwnerId(null);
                applyPublicClaimHint(talent, claimMaps.latestClaims().get(talent.getId()), ownerMap);
            }

            Long sampleCount = sampleCountMap.getOrDefault(talent.getId(), 0L);
            talent.setSampleCount(sampleCount);

            OrderAggregate aggregate = orderAggregateMap.get(talent.getDouyinUid());
            long orderCount = aggregate == null ? 0L : aggregate.orderCount();
            long serviceFee = aggregate == null ? 0L : aggregate.serviceFee();
            long monthlySales = aggregate == null ? 0L : aggregate.orderAmount();
            talent.setOrderCount(orderCount);
            talent.setServiceFeeContribution(serviceFee);
            talent.setMonthlySales(monthlySales);
        }
    }

    private ClaimMaps loadClaimMaps(Set<UUID> talentIds) {
        if (talentIds == null || talentIds.isEmpty()) {
            return new ClaimMaps(Collections.emptyMap(), Collections.emptyMap(), List.of());
        }
        List<TalentClaim> claims = talentClaimMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TalentClaim>()
                        .in(TalentClaim::getTalentId, talentIds)
                        .eq(TalentClaim::getDeleted, 0)
                        .orderByDesc(TalentClaim::getClaimedAt)
        );
        Map<UUID, TalentClaim> activeClaims = new LinkedHashMap<>();
        Map<UUID, TalentClaim> latestClaims = new LinkedHashMap<>();
        for (TalentClaim claim : claims) {
            if (claim.getTalentId() == null) {
                continue;
            }
            latestClaims.putIfAbsent(claim.getTalentId(), claim);
            if (claim.getStatus() != null
                    && claim.getStatus() == CLAIM_STATUS_ACTIVE
                    && !activeClaims.containsKey(claim.getTalentId())) {
                activeClaims.put(claim.getTalentId(), claim);
            }
        }
        return new ClaimMaps(activeClaims, latestClaims, claims);
    }

    private Map<UUID, SysUser> loadOwnerMap(Collection<TalentClaim> claims) {
        if (claims == null || claims.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<UUID> userIds = claims.stream()
                .map(TalentClaim::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return sysUserMapper.selectBatchIds(userIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(SysUser::getId, Function.identity(), (a, b) -> a));
    }

    private Map<UUID, Long> loadSampleCounts(Set<UUID> talentIds) {
        if (talentIds == null || talentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT talent_id, COUNT(1) AS total FROM sample_request WHERE deleted = 0 AND talent_id IS NOT NULL GROUP BY talent_id"
        );
        Map<UUID, Long> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            UUID talentId = parseUuid(row.get("talent_id"));
            if (talentId != null && talentIds.contains(talentId)) {
                result.put(talentId, asLong(row.get("total")));
            }
        }
        return result;
    }

    private Map<String, OrderAggregate> loadOrderAggregates(Set<String> douyinUids) {
        if (douyinUids == null || douyinUids.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT
                    COALESCE(extra_data ->> 'talent_uid', extra_data ->> 'author_id', talent_name) AS talent_uid,
                    COUNT(1) AS order_count,
                    COALESCE(SUM(order_amount), 0) AS order_amount,
                    COALESCE(SUM(settle_colonel_commission), 0) AS service_fee
                FROM colonelsettlement_order
                WHERE deleted = 0
                GROUP BY COALESCE(extra_data ->> 'talent_uid', extra_data ->> 'author_id', talent_name)
                """);
        Map<String, OrderAggregate> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String talentUid = asText(row.get("talent_uid"));
            if (StringUtils.hasText(talentUid) && douyinUids.contains(talentUid)) {
                result.put(talentUid, new OrderAggregate(
                        asLong(row.get("order_count")),
                        asLong(row.get("order_amount")),
                        asLong(row.get("service_fee"))
                ));
            }
        }
        return result;
    }

    private TalentDetailResponse.TalentInfo toTalentInfo(Talent talent) {
        TalentDetailResponse.TalentInfo info = new TalentDetailResponse.TalentInfo();
        info.setId(talent.getId() == null ? null : talent.getId().toString());
        info.setNickname(talent.getNickname());
        info.setDouyinUid(talent.getDouyinUid());
        info.setDouyinNo(talent.getDouyinNo());
        info.setUid(talent.getUid());
        info.setSecUid(talent.getSecUid());
        info.setProfileUrl(talent.getProfileUrl());
        info.setFansCount(talent.getFans());
        info.setLikesCount(talent.getLikesCount());
        info.setWorksCount(talent.getWorksCount());
        info.setIpLocation(talent.getIpLocation());
        info.setLevel(talent.getLevel());
        info.setMonthlySales(talent.getMonthlySales());
        info.setContactPhone(firstNonBlank(talent.getContactPhone(), talent.getContactWechat()));
        info.setRemark(talent.getIntro());
        info.setAvatarUrl(talent.getAvatarUrl());
        return info;
    }

    private TalentDetailResponse.ClaimInfo toClaimInfo(Talent talent) {
        TalentDetailResponse.ClaimInfo info = new TalentDetailResponse.ClaimInfo();
        info.setPoolStatus(talent.getPoolStatus());
        info.setOwnerId(talent.getOwnerId() == null ? null : talent.getOwnerId().toString());
        info.setOwnerName(talent.getOwnerName());
        info.setClaimedAt(talent.getClaimedAt());
        info.setProtectedUntil(talent.getProtectedUntil());
        return info;
    }

    private List<TalentDetailResponse.SampleItem> loadSamples(Talent talent) {
        if (talent == null || talent.getId() == null) {
            return List.of();
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT
                    sr.id,
                    sr.request_no,
                    sr.status,
                    sr.create_time,
                    sr.complete_time,
                    p.name AS product_name
                FROM sample_request sr
                LEFT JOIN product p ON p.id = sr.product_id
                WHERE sr.deleted = 0
                  AND sr.talent_id = ?
                ORDER BY sr.create_time DESC
                LIMIT 20
                """, talent.getId());
        List<TalentDetailResponse.SampleItem> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            TalentDetailResponse.SampleItem item = new TalentDetailResponse.SampleItem();
            item.setSampleRequestId(firstNonBlank(asText(row.get("request_no")), uuidText(row.get("id"))));
            item.setProductName(asText(row.get("product_name")));
            item.setStatus(sampleStatusApi(asInteger(row.get("status"))));
            item.setStatusText(sampleStatusText(item.getStatus()));
            item.setCreateTime(toDateTime(row.get("create_time")));
            item.setCompleteTime(toDateTime(row.get("complete_time")));
            items.add(item);
        }
        return items;
    }

    private List<TalentDetailResponse.OrderItem> loadOrders(Talent talent) {
        if (talent == null || !StringUtils.hasText(talent.getDouyinUid())) {
            return List.of();
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT
                    order_id,
                    COALESCE(product_title, product_name) AS product_name,
                    order_amount,
                    settle_colonel_commission,
                    channel_user_name,
                    create_time
                FROM colonelsettlement_order
                WHERE deleted = 0
                  AND COALESCE(extra_data ->> 'talent_uid', extra_data ->> 'author_id', talent_name) = ?
                ORDER BY create_time DESC
                LIMIT 20
                """, talent.getDouyinUid());
        List<TalentDetailResponse.OrderItem> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            TalentDetailResponse.OrderItem item = new TalentDetailResponse.OrderItem();
            item.setOrderId(asText(row.get("order_id")));
            item.setProductName(asText(row.get("product_name")));
            item.setOrderAmount(asLong(row.get("order_amount")));
            item.setServiceFee(asLong(row.get("settle_colonel_commission")));
            item.setChannelName(asText(row.get("channel_user_name")));
            item.setCreateTime(toDateTime(row.get("create_time")));
            items.add(item);
        }
        return items;
    }

    private String displayName(SysUser user) {
        return firstNonBlank(user.getRealName(), user.getUsername(), user.getId() == null ? null : user.getId().toString());
    }

    private void applyPublicClaimHint(Talent talent, TalentClaim latestClaim, Map<UUID, SysUser> ownerMap) {
        if (latestClaim == null) {
            talent.setOwnerName(null);
            talent.setClaimedAt(null);
            talent.setProtectedUntil(null);
            return;
        }
        talent.setClaimedAt(latestClaim.getClaimedAt());
        talent.setProtectedUntil(latestClaim.getProtectedUntil());
        if (latestClaim.getStatus() != null && latestClaim.getStatus() == CLAIM_STATUS_EXPIRED) {
            SysUser owner = ownerMap.get(latestClaim.getUserId());
            String ownerName = owner == null ? null : displayName(owner);
            talent.setOwnerName(StringUtils.hasText(ownerName) ? "已过期释放 · 原归属 " + ownerName : "已过期释放");
            return;
        }
        talent.setOwnerName(null);
    }

    private boolean matchesPoolStatus(Talent talent, String poolStatus) {
        if (!StringUtils.hasText(poolStatus)) {
            return true;
        }
        return poolStatus.equalsIgnoreCase(firstNonBlank(talent.getPoolStatus(), "PUBLIC"));
    }

    private boolean matchesOwnerKeyword(Talent talent, String ownerKeyword) {
        if (!StringUtils.hasText(ownerKeyword)) {
            return true;
        }
        if (!"PRIVATE".equalsIgnoreCase(firstNonBlank(talent.getPoolStatus(), "PUBLIC"))) {
            return false;
        }
        String ownerName = firstNonBlank(talent.getOwnerName(), "");
        return ownerName.toLowerCase(Locale.ROOT).contains(ownerKeyword.toLowerCase(Locale.ROOT));
    }

    private String sampleStatusApi(Integer status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case 1 -> "PENDING_AUDIT";
            case 2 -> "PENDING_SHIP";
            case 3, 4 -> "SHIPPED";
            case 5 -> "PENDING_TASK";
            case 6 -> "FINISHED";
            case 7 -> "REJECTED";
            case 8 -> "CLOSED";
            default -> String.valueOf(status);
        };
    }

    private String sampleStatusText(String status) {
        if (!StringUtils.hasText(status)) {
            return "-";
        }
        return switch (status) {
            case "PENDING_AUDIT" -> "待审核";
            case "PENDING_SHIP" -> "待发货";
            case "SHIPPED" -> "快递中";
            case "PENDING_TASK" -> "待交作业";
            case "FINISHED" -> "已完成";
            case "REJECTED" -> "已拒绝";
            case "CLOSED" -> "已关闭";
            default -> status;
        };
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String asText(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }

    private long asLong(Object raw) {
        if (raw == null) {
            return 0L;
        }
        if (raw instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(raw));
        } catch (Exception ex) {
            return 0L;
        }
    }

    private Integer asInteger(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (Exception ex) {
            return null;
        }
    }

    private UUID parseUuid(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return raw instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(raw));
        } catch (Exception ex) {
            return null;
        }
    }

    private String uuidText(Object raw) {
        UUID uuid = parseUuid(raw);
        return uuid == null ? null : uuid.toString();
    }

    private LocalDateTime toDateTime(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof LocalDateTime time) {
            return time;
        }
        if (raw instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }

    private record OrderAggregate(long orderCount, long orderAmount, long serviceFee) {
    }

    private record ClaimMaps(
            Map<UUID, TalentClaim> activeClaims,
            Map<UUID, TalentClaim> latestClaims,
            List<TalentClaim> allClaims) {
    }
}
