package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.talent.TalentDetailResponse;
import com.colonel.saas.dto.talent.TalentPageQuery;
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
    private static final int TALENT_QUERY_BATCH_SIZE = 200;
    private static final int SQL_IN_BATCH_SIZE = 200;

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

    public IPage<Talent> page(TalentPageQuery query) {
        long requestedPage = query == null ? 1L : Math.max(query.getPage(), 1L);
        long requestedSize = query == null ? 10L : Math.max(query.getSize(), 1L);
        long fetchSize = normalizeFetchSize(requestedSize);
        long fromIndex = Math.max(0L, (requestedPage - 1) * requestedSize);
        long toIndexExclusive = fromIndex + requestedSize;

        List<Talent> pageRecords = new ArrayList<>();
        long filteredTotal = 0L;
        long current = 1L;
        long pages = 1L;
        DataScope baseScope = resolveBaseScope(query);
        String listKeyword = resolveListKeyword(query);
        while (current <= pages) {
            IPage<Talent> batchPage = talentService.page(
                    current,
                    fetchSize,
                    listKeyword,
                    query.getRegion(),
                    query.getMinFans(),
                    query.getMaxFans(),
                    baseScope,
                    query.getUserId(),
                    query.getDeptId());
            List<Talent> records = new ArrayList<>(batchPage.getRecords());
            enrichTalentCards(records, query.getUserId());
            for (Talent talent : records) {
                if (!matchesPoolStatus(talent, query.getPoolStatus())
                        || !matchesOwnerKeyword(talent, query.getOwnerKeyword())
                        || !matchesView(talent, query)
                        || !matchesPlatform(query.getPlatform())
                        || !matchesClaimStatus(talent, query.getClaimStatus())
                        || !matchesCategory(talent, query.getCategory())
                        || !matchesLevel(talent, query.getLevel())
                        || !matchesRegion(talent, query.getRegion())
                        || !matchesDouyinNo(talent, query.getDouyinNo())
                        || !matchesNickname(talent, query.getNickname())
                        || !matchesMetricBand(talent.getLiveSalesBand(), query.getLiveSalesBand())
                        || !matchesMetricBand(talent.getLiveViewBand(), query.getLiveViewBand())
                        || !matchesMetricBand(talent.getLiveGpmBand(), query.getLiveGpmBand())
                        || !matchesMetricBand(talent.getVideoSalesBand(), query.getVideoSalesBand())
                        || !matchesMetricBand(talent.getVideoPlayBand(), query.getVideoPlayBand())
                        || !matchesMetricBand(talent.getVideoGpmBand(), query.getVideoGpmBand())
                        || !matchesContactStatus(talent, query.getContactStatus())) {
                    continue;
                }
                if (filteredTotal >= fromIndex && filteredTotal < toIndexExclusive) {
                    pageRecords.add(talent);
                }
                filteredTotal++;
            }
            pages = batchPage.getPages();
            if (pages <= 0 || batchPage.getRecords().isEmpty()) {
                break;
            }
            current++;
        }

        Page<Talent> result = new Page<>(requestedPage, requestedSize, filteredTotal);
        result.setRecords(pageRecords);
        return result;
    }

    private long normalizeFetchSize(long requestedSize) {
        if (requestedSize <= 0) {
            return 10L;
        }
        return Math.min(requestedSize, TALENT_QUERY_BATCH_SIZE);
    }

    private DataScope resolveBaseScope(TalentPageQuery query) {
        if (query == null) {
            return DataScope.ALL;
        }
        if ("TEAM_PUBLIC".equalsIgnoreCase(firstNonBlank(query.getView(), ""))) {
            return DataScope.ALL;
        }
        return query.getDataScope() != null ? query.getDataScope() : DataScope.ALL;
    }

    public TalentDetailResponse detail(UUID talentId, UUID currentUserId, UUID currentDeptId, DataScope dataScope) {
        Talent talent = talentService.getById(talentId);
        UUID resolvedTalentId = talent == null ? null : talent.getId();
        ClaimMaps claimMaps = resolvedTalentId == null
                ? new ClaimMaps(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), List.of())
                : loadClaimMaps(Set.of(resolvedTalentId));
        assertCanAccess(talent, currentUserId, currentDeptId, dataScope,
                claimMaps.activeClaimsByTalent().getOrDefault(resolvedTalentId, List.of()));
        enrichTalentCards(List.of(talent), currentUserId, claimMaps);

        boolean redactSensitiveFields = shouldRedactSensitiveFields(dataScope);
        TalentDetailResponse response = new TalentDetailResponse();
        response.setTalent(toTalentInfo(talent, redactSensitiveFields));
        response.setClaim(toClaimInfo(
                talent,
                currentUserId,
                redactSensitiveFields,
                claimMaps.activeClaimsByTalent().getOrDefault(resolvedTalentId, List.of())));
        response.setSamples(loadSamples(talent));
        response.setOrders(loadOrders(talent, redactSensitiveFields));
        return response;
    }

    public void assertCanOperate(UUID talentId, UUID currentUserId, UUID currentDeptId, Collection<?> roleCodes) {
        Talent talent = talentService.getById(talentId);
        UUID resolvedTalentId = talent == null ? null : talent.getId();
        if (resolvedTalentId == null || hasRole(roleCodes, RoleCodes.ADMIN)) {
            return;
        }
        List<TalentClaim> activeClaims = talentClaimMapper.findActiveByTalentId(resolvedTalentId);
        List<TalentClaim> safeActiveClaims = activeClaims == null ? List.of() : activeClaims;
        if (hasRole(roleCodes, RoleCodes.CHANNEL_LEADER)) {
            boolean ownedByCurrentDept = currentDeptId != null && safeActiveClaims.stream()
                    .anyMatch(claim -> currentDeptId.equals(claim.getDeptId()));
            if (ownedByCurrentDept) {
                return;
            }
        }
        if (hasRole(roleCodes, RoleCodes.CHANNEL_STAFF)) {
            boolean ownedByCurrentUser = currentUserId != null && safeActiveClaims.stream()
                    .anyMatch(claim -> currentUserId.equals(claim.getUserId()));
            if (ownedByCurrentUser) {
                return;
            }
        }
        throw new ForbiddenException("无权操作该达人");
    }

    private void assertCanAccess(Talent talent, UUID currentUserId, UUID currentDeptId, DataScope dataScope) {
        List<TalentClaim> activeClaims = talent == null || talent.getId() == null
                ? List.of()
                : talentClaimMapper.findActiveByTalentId(talent.getId());
        assertCanAccess(talent, currentUserId, currentDeptId, dataScope, activeClaims);
    }

    private void assertCanAccess(
            Talent talent,
            UUID currentUserId,
            UUID currentDeptId,
            DataScope dataScope,
            List<TalentClaim> activeClaims) {
        if (talent == null || talent.getId() == null || dataScope == null || dataScope == DataScope.ALL) {
            return;
        }
        List<TalentClaim> safeActiveClaims = activeClaims == null ? List.of() : activeClaims;
        if (dataScope == DataScope.PERSONAL) {
            boolean ownedByCurrentUser = currentUserId != null && safeActiveClaims.stream()
                    .anyMatch(claim -> currentUserId.equals(claim.getUserId()));
            if (!ownedByCurrentUser) {
                throw new ForbiddenException("无权查看该达人详情");
            }
            return;
        }
        boolean ownedByCurrentDept = currentDeptId != null && safeActiveClaims.stream()
                .anyMatch(claim -> currentDeptId.equals(claim.getDeptId()));
        if (!ownedByCurrentDept) {
            throw new ForbiddenException("无权查看该达人详情");
        }
    }

    private boolean hasRole(Collection<?> roleCodes, String expectedRole) {
        if (roleCodes == null || roleCodes.isEmpty() || !StringUtils.hasText(expectedRole)) {
            return false;
        }
        String normalizedExpected = expectedRole.trim().toLowerCase(Locale.ROOT);
        return roleCodes.stream()
                .filter(Objects::nonNull)
                .map(value -> value.toString().trim().toLowerCase(Locale.ROOT))
                .anyMatch(normalizedExpected::equals);
    }

    private void enrichTalentCards(List<Talent> talents, UUID currentUserId) {
        enrichTalentCards(talents, currentUserId, null);
    }

    private void enrichTalentCards(List<Talent> talents, UUID currentUserId, ClaimMaps preloadedClaimMaps) {
        if (talents == null || talents.isEmpty()) {
            return;
        }

        Set<UUID> talentIds = talents.stream().map(Talent::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        ClaimMaps claimMaps = preloadedClaimMaps == null ? loadClaimMaps(talentIds) : preloadedClaimMaps;
        Map<UUID, SysUser> ownerMap = loadOwnerMap(claimMaps.allClaims());
        Map<UUID, Long> sampleCountMap = loadSampleCounts(
                talentIds
        );
        Map<String, OrderAggregate> orderAggregateMap = loadOrderAggregates(
                talents.stream().map(Talent::getDouyinUid).filter(StringUtils::hasText).collect(Collectors.toSet())
        );

        for (Talent talent : talents) {
            List<TalentClaim> activeClaims = claimMaps.activeClaimsByTalent().getOrDefault(talent.getId(), List.of());
            TalentClaim currentClaim = activeClaims.stream()
                    .filter(claim -> currentUserId != null && currentUserId.equals(claim.getUserId()))
                    .findFirst()
                    .orElse(null);
            talent.setActiveClaimCount(activeClaims.size());
            if (currentClaim != null) {
                talent.setPoolStatus("PRIVATE");
                talent.setOwnerId(currentClaim.getUserId());
                talent.setClaimedAt(currentClaim.getClaimedAt());
                talent.setProtectedUntil(currentClaim.getProtectedUntil());
                talent.setOwnerName(buildClaimSummary(activeClaims, ownerMap, currentUserId));
            } else {
                talent.setPoolStatus("PUBLIC");
                talent.setOwnerId(null);
                applyPublicClaimHint(talent, activeClaims, claimMaps.latestClaims().get(talent.getId()), ownerMap);
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
            talent.setNaturalOrderTalent(orderCount > 0);
            talent.setMainCategory(resolveMainCategory(talent.getCategories()));
            talent.setLiveSalesBand(toSalesBand(monthlySales));
            talent.setLiveViewBand(toFansBand(talent.getFans()));
            talent.setLiveGpmBand(toGpmBand(monthlySales, talent.getFans()));
            talent.setVideoSalesBand(orderCount > 0 ? toSalesBand(Math.max(monthlySales / Math.max(orderCount, 1), 0L)) : null);
            talent.setVideoPlayBand(orderCount > 0 ? toPlayBand(talent.getFans(), orderCount) : null);
            talent.setVideoGpmBand(orderCount > 0 ? toGpmBand(Math.max(monthlySales / Math.max(orderCount, 1), 0L), talent.getFans()) : null);
        }
    }

    private ClaimMaps loadClaimMaps(Set<UUID> talentIds) {
        if (talentIds == null || talentIds.isEmpty()) {
            return new ClaimMaps(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), List.of());
        }
        List<TalentClaim> claims = talentClaimMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TalentClaim>()
                        .in(TalentClaim::getTalentId, talentIds)
                        .eq(TalentClaim::getDeleted, 0)
                        .orderByDesc(TalentClaim::getClaimedAt)
        );
        Map<UUID, TalentClaim> activeClaims = new LinkedHashMap<>();
        Map<UUID, List<TalentClaim>> activeClaimsByTalent = new LinkedHashMap<>();
        Map<UUID, TalentClaim> latestClaims = new LinkedHashMap<>();
        for (TalentClaim claim : claims) {
            if (claim.getTalentId() == null) {
                continue;
            }
            latestClaims.putIfAbsent(claim.getTalentId(), claim);
            if (claim.getStatus() != null && claim.getStatus() == CLAIM_STATUS_ACTIVE) {
                activeClaimsByTalent.computeIfAbsent(claim.getTalentId(), key -> new ArrayList<>()).add(claim);
                if (!activeClaims.containsKey(claim.getTalentId())) {
                    activeClaims.put(claim.getTalentId(), claim);
                }
            }
        }
        return new ClaimMaps(activeClaims, activeClaimsByTalent, latestClaims, claims);
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
        Map<UUID, Long> result = new HashMap<>();
        for (List<UUID> batch : partition(talentIds, SQL_IN_BATCH_SIZE)) {
            String placeholders = joinPlaceholders(batch.size());
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT talent_id, COUNT(1) AS total FROM sample_request " +
                            "WHERE deleted = 0 AND talent_id IS NOT NULL AND talent_id IN (" + placeholders + ") " +
                            "GROUP BY talent_id",
                    batch.toArray()
            );
            for (Map<String, Object> row : rows) {
                UUID talentId = parseUuid(row.get("talent_id"));
                if (talentId != null) {
                    result.put(talentId, asLong(row.get("total")));
                }
            }
        }
        return result;
    }

    private Map<String, OrderAggregate> loadOrderAggregates(Set<String> douyinUids) {
        if (douyinUids == null || douyinUids.isEmpty()) {
            return Collections.emptyMap();
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        Map<String, OrderAggregate> result = new HashMap<>();
        for (List<String> batch : partition(douyinUids, SQL_IN_BATCH_SIZE)) {
            String placeholders = joinPlaceholders(batch.size());
            List<Object> params = new ArrayList<>(batch.size() + 1);
            params.add(Timestamp.valueOf(cutoff));
            params.addAll(batch);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT
                        COALESCE(extra_data ->> 'talent_uid', extra_data ->> 'author_id', talent_name) AS talent_uid,
                        COUNT(1) AS order_count,
                        COALESCE(SUM(order_amount), 0) AS order_amount,
                        COALESCE(SUM(settle_colonel_commission), 0) AS service_fee
                    FROM colonelsettlement_order
                    WHERE deleted = 0
                      AND create_time >= ?
                      AND COALESCE(extra_data ->> 'talent_uid', extra_data ->> 'author_id', talent_name) IN (""" + placeholders + ") " +
                    "GROUP BY COALESCE(extra_data ->> 'talent_uid', extra_data ->> 'author_id', talent_name)",
                    params.toArray()
            );
            for (Map<String, Object> row : rows) {
                String talentUid = asText(row.get("talent_uid"));
                if (StringUtils.hasText(talentUid)) {
                    result.put(talentUid, new OrderAggregate(
                            asLong(row.get("order_count")),
                            asLong(row.get("order_amount")),
                            asLong(row.get("service_fee"))
                    ));
                }
            }
        }
        return result;
    }

    private String joinPlaceholders(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
        return String.join(", ", Collections.nCopies(size, "?"));
    }

    private <T> List<List<T>> partition(Collection<T> values, int batchSize) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<T> list = values instanceof List<?> existing ? (List<T>) existing : new ArrayList<>(values);
        List<List<T>> partitions = new ArrayList<>();
        for (int index = 0; index < list.size(); index += batchSize) {
            partitions.add(list.subList(index, Math.min(index + batchSize, list.size())));
        }
        return partitions;
    }

    private TalentDetailResponse.TalentInfo toTalentInfo(Talent talent, boolean redactSensitiveFields) {
        TalentDetailResponse.TalentInfo info = new TalentDetailResponse.TalentInfo();
        info.setId(talent.getId() == null ? null : talent.getId().toString());
        info.setNickname(talent.getNickname());
        info.setDouyinUid(talent.getDouyinUid());
        info.setDouyinNo(talent.getDouyinNo());
        info.setUid(talent.getUid());
        if (!redactSensitiveFields) {
            info.setProfileUrl(talent.getProfileUrl());
        }
        info.setFansCount(talent.getFans());
        info.setLikesCount(talent.getLikesCount());
        info.setWorksCount(talent.getWorksCount());
        info.setIpLocation(talent.getIpLocation());
        info.setLevel(talent.getLevel());
        info.setMonthlySales(talent.getMonthlySales());
        info.setMainCategory(talent.getMainCategory());
        info.setLiveSalesBand(talent.getLiveSalesBand());
        info.setLiveViewBand(talent.getLiveViewBand());
        info.setLiveGpmBand(talent.getLiveGpmBand());
        info.setVideoSalesBand(talent.getVideoSalesBand());
        info.setVideoPlayBand(talent.getVideoPlayBand());
        info.setVideoGpmBand(talent.getVideoGpmBand());
        info.setBlacklisted(Boolean.TRUE.equals(talent.getBlacklisted()));
        info.setBlacklistReason(talent.getBlacklistReason());
        info.setOrderCount(talent.getOrderCount());
        info.setSampleCount(talent.getSampleCount());
        info.setServiceFeeContribution(talent.getServiceFeeContribution());
        info.setContactPhone(firstNonBlank(talent.getContactPhone(), talent.getContactWechat()));
        info.setRemark(talent.getIntro());
        info.setAvatarUrl(talent.getAvatarUrl());
        info.setTags(talent.getTags());
        info.setTagUpdatedBy(talent.getTagUpdatedBy() == null ? null : talent.getTagUpdatedBy().toString());
        info.setShippingRecipientName(talent.getShippingRecipientName());
        info.setShippingRecipientPhone(talent.getShippingRecipientPhone());
        info.setShippingRecipientAddress(talent.getShippingRecipientAddress());
        return info;
    }

    private TalentDetailResponse.ClaimInfo toClaimInfo(
            Talent talent,
            UUID currentUserId,
            boolean redactSensitiveFields,
            List<TalentClaim> activeClaims) {
        TalentDetailResponse.ClaimInfo info = new TalentDetailResponse.ClaimInfo();
        info.setPoolStatus(talent.getPoolStatus());
        info.setOwnerId(talent.getOwnerId() == null ? null : talent.getOwnerId().toString());
        info.setOwnerName(talent.getOwnerName());
        info.setClaimedAt(talent.getClaimedAt());
        info.setProtectedUntil(talent.getProtectedUntil());
        info.setActiveClaimCount(talent.getActiveClaimCount());
        TalentClaim claimAddress = resolveClaimAddress(activeClaims, currentUserId);
        if (claimAddress != null) {
            info.setRecipientName(claimAddress.getRecipientName());
            info.setRecipientPhone(claimAddress.getRecipientPhone());
            info.setRecipientAddress(claimAddress.getRecipientAddress());
        }
        info.setActiveClaimOwners(redactSensitiveFields ? List.of() : loadActiveClaimOwners(talent.getId(), currentUserId));
        return info;
    }

    private TalentClaim resolveClaimAddress(List<TalentClaim> activeClaims, UUID currentUserId) {
        if (activeClaims == null || activeClaims.isEmpty()) {
            return null;
        }
        if (currentUserId != null) {
            for (TalentClaim claim : activeClaims) {
                if (currentUserId.equals(claim.getUserId())) {
                    return claim;
                }
            }
        }
        return activeClaims.get(0);
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

    private List<TalentDetailResponse.OrderItem> loadOrders(Talent talent, boolean redactSensitiveFields) {
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
            if (!redactSensitiveFields) {
                item.setChannelName(asText(row.get("channel_user_name")));
            }
            item.setCreateTime(toDateTime(row.get("create_time")));
            items.add(item);
        }
        return items;
    }

    private boolean shouldRedactSensitiveFields(DataScope dataScope) {
        return dataScope == DataScope.PERSONAL;
    }

    private String displayName(SysUser user) {
        return firstNonBlank(user.getRealName(), user.getUsername(), user.getId() == null ? null : user.getId().toString());
    }

    private void applyPublicClaimHint(Talent talent,
                                      List<TalentClaim> activeClaims,
                                      TalentClaim latestClaim,
                                      Map<UUID, SysUser> ownerMap) {
        if (activeClaims != null && !activeClaims.isEmpty()) {
            talent.setOwnerName(buildClaimSummary(activeClaims, ownerMap, null));
            TalentClaim latestActiveClaim = activeClaims.get(0);
            talent.setClaimedAt(latestActiveClaim.getClaimedAt());
            talent.setProtectedUntil(activeClaims.stream()
                    .map(TalentClaim::getProtectedUntil)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(latestActiveClaim.getProtectedUntil()));
            return;
        }
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

    private String buildClaimSummary(List<TalentClaim> claims, Map<UUID, SysUser> ownerMap, UUID currentUserId) {
        if (claims == null || claims.isEmpty()) {
            return null;
        }
        List<String> names = claims.stream()
                .map(TalentClaim::getUserId)
                .filter(Objects::nonNull)
                .map(ownerMap::get)
                .filter(Objects::nonNull)
                .map(this::displayName)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        String primaryName = null;
        if (currentUserId != null) {
            primaryName = claims.stream()
                    .filter(claim -> currentUserId.equals(claim.getUserId()))
                    .map(TalentClaim::getUserId)
                    .map(ownerMap::get)
                    .filter(Objects::nonNull)
                    .map(this::displayName)
                    .filter(StringUtils::hasText)
                    .findFirst()
                    .orElse(null);
        }
        if (!StringUtils.hasText(primaryName) && !names.isEmpty()) {
            primaryName = names.get(0);
        }
        if (!StringUtils.hasText(primaryName)) {
            primaryName = "多人认领";
        }
        if (names.size() <= 1) {
            return primaryName;
        }
        return primaryName + " 等 " + names.size() + " 人";
    }

    private List<TalentDetailResponse.ClaimOwnerItem> loadActiveClaimOwners(UUID talentId, UUID currentUserId) {
        if (talentId == null) {
            return List.of();
        }
        List<TalentClaim> activeClaims = talentClaimMapper.findActiveByTalentId(talentId);
        if (activeClaims == null || activeClaims.isEmpty()) {
            return List.of();
        }
        Map<UUID, SysUser> ownerMap = loadOwnerMap(activeClaims);
        return activeClaims.stream()
                .map(claim -> {
                    TalentDetailResponse.ClaimOwnerItem item = new TalentDetailResponse.ClaimOwnerItem();
                    item.setUserId(claim.getUserId() == null ? null : claim.getUserId().toString());
                    SysUser owner = claim.getUserId() == null ? null : ownerMap.get(claim.getUserId());
                    String ownerName = owner == null ? null : displayName(owner);
                    if (currentUserId != null && currentUserId.equals(claim.getUserId()) && StringUtils.hasText(ownerName)) {
                        ownerName = ownerName + "（我）";
                    }
                    item.setOwnerName(ownerName);
                    item.setClaimedAt(claim.getClaimedAt());
                    item.setProtectedUntil(claim.getProtectedUntil());
                    return item;
                })
                .toList();
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

    private boolean matchesView(Talent talent, TalentPageQuery query) {
        String view = query.getView();
        if (!StringUtils.hasText(view)) {
            return true;
        }
        boolean personalPublicView = "TEAM_PUBLIC".equalsIgnoreCase(view)
                && query.getDataScope() == DataScope.PERSONAL;
        return switch (view) {
            case "TEAM_PUBLIC" -> "PUBLIC".equalsIgnoreCase(firstNonBlank(talent.getPoolStatus(), "PUBLIC"))
                    && !Boolean.TRUE.equals(talent.getBlacklisted())
                    && (!personalPublicView || itemOrZero(talent.getActiveClaimCount()) == 0);
            case "MY_TALENTS" -> "PRIVATE".equalsIgnoreCase(firstNonBlank(talent.getPoolStatus(), "PUBLIC"))
                    && Objects.equals(talent.getOwnerId(), query.getUserId());
            case "TEAM_PRIVATE" -> itemOrZero(talent.getActiveClaimCount()) > 0
                    && !Boolean.TRUE.equals(talent.getBlacklisted());
            case "NATURAL_ORDERS" -> Boolean.TRUE.equals(talent.getNaturalOrderTalent());
            case "BLACKLIST" -> Boolean.TRUE.equals(talent.getBlacklisted());
            default -> true;
        };
    }

    private long itemOrZero(Number value) {
        return value == null ? 0L : value.longValue();
    }

    private boolean matchesClaimStatus(Talent talent, String claimStatus) {
        if (!StringUtils.hasText(claimStatus)) {
            return true;
        }
        return switch (claimStatus) {
            case "CLAIMED" -> "PRIVATE".equalsIgnoreCase(firstNonBlank(talent.getPoolStatus(), "PUBLIC"));
            case "UNCLAIMED" -> "PUBLIC".equalsIgnoreCase(firstNonBlank(talent.getPoolStatus(), "PUBLIC"));
            default -> true;
        };
    }

    private boolean matchesCategory(Talent talent, String category) {
        if (!StringUtils.hasText(category)) {
            return true;
        }
        String needle = category.trim();
        String mainCategory = firstNonBlank(
                talent.getMainCategory(),
                resolveMainCategory(talent.getCategories()),
                "");
        if (mainCategory.contains(needle)) {
            return true;
        }
        return firstNonBlank(talent.getCategories(), "").contains(needle);
    }

    private boolean matchesLevel(Talent talent, String level) {
        if (!StringUtils.hasText(level)) {
            return true;
        }
        String expected = level.trim();
        String actual = firstNonBlank(talent.getLevel(), talent.getTalentLevel(), "");
        if (!StringUtils.hasText(actual)) {
            return false;
        }
        if (expected.equalsIgnoreCase(actual)) {
            return true;
        }
        String expectedDigits = expected.replaceAll("(?i)^lv", "");
        String actualDigits = actual.replaceAll("(?i)^lv", "");
        return expectedDigits.equalsIgnoreCase(actual)
                || expectedDigits.equalsIgnoreCase(actualDigits);
    }

    private String resolveListKeyword(TalentPageQuery query) {
        if (query == null) {
            return null;
        }
        if (StringUtils.hasText(query.getKeyword())) {
            return query.getKeyword().trim();
        }
        if (StringUtils.hasText(query.getNickname())) {
            return query.getNickname().trim();
        }
        if (StringUtils.hasText(query.getDouyinNo())) {
            return query.getDouyinNo().trim();
        }
        return null;
    }

    private boolean matchesRegion(Talent talent, String region) {
        if (!StringUtils.hasText(region)) {
            return true;
        }
        return firstNonBlank(talent.getIpLocation(), "").contains(region);
    }

    private boolean matchesPlatform(String platform) {
        if (!StringUtils.hasText(platform)) {
            return true;
        }
        return !"kuaishou".equalsIgnoreCase(platform.trim());
    }

    private boolean matchesDouyinNo(Talent talent, String douyinNo) {
        if (!StringUtils.hasText(douyinNo)) {
            return true;
        }
        String normalized = douyinNo.trim();
        return firstNonBlank(talent.getDouyinNo(), talent.getDouyinUid(), talent.getUid(), "")
                .contains(normalized);
    }

    private boolean matchesNickname(Talent talent, String nickname) {
        if (!StringUtils.hasText(nickname)) {
            return true;
        }
        return firstNonBlank(talent.getNickname(), "").contains(nickname.trim());
    }

    private boolean matchesMetricBand(String actual, String expected) {
        if (!StringUtils.hasText(expected)) {
            return true;
        }
        return expected.equals(actual);
    }

    private boolean matchesContactStatus(Talent talent, String contactStatus) {
        if (!StringUtils.hasText(contactStatus)) {
            return true;
        }
        boolean hasContact = StringUtils.hasText(talent.getContactPhone())
                || StringUtils.hasText(talent.getContactWechat());
        return switch (contactStatus) {
            case "HAS_CONTACT" -> hasContact;
            case "NO_CONTACT" -> !hasContact;
            default -> true;
        };
    }

    private String resolveMainCategory(String categories) {
        if (!StringUtils.hasText(categories)) {
            return null;
        }
        String normalized = categories.replace("[", "")
                .replace("]", "")
                .replace("\"", "")
                .replace("{", "")
                .replace("}", "");
        String[] parts = normalized.split("[,，]");
        for (String part : parts) {
            if (StringUtils.hasText(part)) {
                return part.trim();
            }
        }
        return StringUtils.hasText(normalized) ? normalized.trim() : null;
    }

    private String toSalesBand(Long amount) {
        if (amount == null || amount <= 0) {
            return null;
        }
        long yuan = amount / 100;
        if (yuan < 10000) return "1W以下";
        if (yuan < 25000) return "1W~2.5W";
        if (yuan < 50000) return "2.5W~5W";
        if (yuan < 75000) return "5W~7.5W";
        if (yuan < 100000) return "7.5W~10W";
        if (yuan < 250000) return "10W~25W";
        if (yuan < 500000) return "25W~50W";
        return "50W以上";
    }

    private String toFansBand(Long fans) {
        if (fans == null || fans <= 0) {
            return null;
        }
        if (fans < 10000) return "1W以下";
        if (fans < 50000) return "1W~5W";
        if (fans < 100000) return "5W~10W";
        if (fans < 500000) return "10W~50W";
        if (fans < 1000000) return "50W~100W";
        return "100W以上";
    }

    private String toPlayBand(Long fans, long orderCount) {
        if (fans == null || fans <= 0 || orderCount <= 0) {
            return null;
        }
        long score = Math.max(fans / Math.max(orderCount, 1), 1);
        if (score < 5000) return "5千以下";
        if (score < 10000) return "5千~1W";
        if (score < 50000) return "1W~5W";
        return "5W以上";
    }

    private String toGpmBand(Long amount, Long fans) {
        if (amount == null || amount <= 0 || fans == null || fans <= 0) {
            return null;
        }
        long gpm = Math.max((amount / 100) * 1000 / fans, 1);
        if (gpm < 100) return "50~100";
        if (gpm < 500) return "100~500";
        if (gpm < 1000) return "500~1000";
        return "1000+";
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
            Map<UUID, List<TalentClaim>> activeClaimsByTalent,
            Map<UUID, TalentClaim> latestClaims,
            List<TalentClaim> allClaims) {
    }
}
