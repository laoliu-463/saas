package com.colonel.saas.domain.talent.facade;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.common.exception.OptimisticLockSupport;
import com.colonel.saas.domain.talent.facade.dto.TalentClaimAddressDTO;
import com.colonel.saas.domain.talent.facade.dto.TalentComplaintRiskDTO;
import com.colonel.saas.domain.talent.facade.dto.TalentReadDTO;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.entity.TalentClaim;
import com.colonel.saas.mapper.TalentClaimMapper;
import com.colonel.saas.mapper.TalentComplaintMapper;
import com.colonel.saas.mapper.TalentMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@link TalentDomainFacade} 遗留实现：委派现有 Mapper，零行为变更（DDD-TALENT-001）。
 */
@Service
public class LegacyTalentDomainFacade implements TalentDomainFacade {

    private final TalentMapper talentMapper;
    private final TalentClaimMapper talentClaimMapper;
    private final TalentComplaintMapper talentComplaintMapper;

    @Autowired
    public LegacyTalentDomainFacade(
            TalentMapper talentMapper,
            TalentClaimMapper talentClaimMapper,
            TalentComplaintMapper talentComplaintMapper) {
        this.talentMapper = talentMapper;
        this.talentClaimMapper = talentClaimMapper;
        this.talentComplaintMapper = talentComplaintMapper;
    }

    /**
     * 保留既有测试和非 Spring 调用方的构造方式。
     */
    public LegacyTalentDomainFacade(TalentMapper talentMapper, TalentClaimMapper talentClaimMapper) {
        this(talentMapper, talentClaimMapper, null);
    }

    @Override
    public TalentClaimAddressDTO findActiveClaimAddress(UUID talentId, UUID ownerUserId) {
        if (talentId == null || ownerUserId == null) {
            return null;
        }
        TalentClaim claim = talentClaimMapper.findActiveByTalentAndUser(talentId, ownerUserId);
        if (claim == null) {
            return null;
        }
        return new TalentClaimAddressDTO(
                claim.getTalentId(),
                claim.getUserId(),
                claim.getRecipientName(),
                claim.getRecipientPhone(),
                claim.getRecipientAddress());
    }

    @Override
    public void updateActiveClaimAddress(
            UUID talentId,
            UUID ownerUserId,
            String recipientName,
            String recipientPhone,
            String recipientAddress) {
        TalentClaim claim = talentId == null || ownerUserId == null
                ? null
                : talentClaimMapper.findActiveByTalentAndUser(talentId, ownerUserId);
        if (claim == null) {
            OptimisticLockSupport.requireUpdated(0, "达人认领地址已失效，请刷新后重试");
            return;
        }
        claim.setRecipientName(recipientName);
        claim.setRecipientPhone(recipientPhone);
        claim.setRecipientAddress(recipientAddress);
        OptimisticLockSupport.requireUpdated(
                talentClaimMapper.updateById(claim),
                "达人认领地址已被修改或认领已失效，请刷新后重试");
    }

    @Override
    public Map<UUID, TalentComplaintRiskDTO> loadComplaintRisks(Collection<UUID> talentIds) {
        if (talentIds == null || talentIds.isEmpty() || talentComplaintMapper == null) {
            return Map.of();
        }
        List<UUID> distinctTalentIds = talentIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (distinctTalentIds.isEmpty()) {
            return Map.of();
        }
        List<TalentComplaintMapper.TalentRiskSummary> summaries =
                talentComplaintMapper.selectRiskSummariesByTalentIds(distinctTalentIds);
        if (summaries == null || summaries.isEmpty()) {
            return Map.of();
        }
        Map<UUID, TalentComplaintRiskDTO> risks = new LinkedHashMap<>();
        for (TalentComplaintMapper.TalentRiskSummary summary : summaries) {
            if (summary == null || summary.talentId() == null) {
                continue;
            }
            risks.putIfAbsent(summary.talentId(), new TalentComplaintRiskDTO(
                    summary.talentId(),
                    summary.complaintCount(),
                    summary.latestComplaintAt()));
        }
        return risks;
    }

    @Override
    public TalentReadDTO findTalentById(UUID talentId) {
        if (talentId == null) {
            return null;
        }
        return toTalentRead(talentMapper.selectById(talentId));
    }

    @Override
    public TalentReadDTO findByDouyinUid(String douyinUid) {
        if (!StringUtils.hasText(douyinUid)) {
            return null;
        }
        Talent talent = talentMapper.selectOne(new LambdaQueryWrapper<Talent>()
                .eq(Talent::getDouyinUid, douyinUid.trim())
                .eq(Talent::getDeleted, 0)
                .last("LIMIT 1"));
        return toTalentRead(talent);
    }

    @Override
    public boolean existsById(UUID talentId) {
        if (talentId == null) {
            return false;
        }
        return talentMapper.selectById(talentId) != null;
    }

    @Override
    public Map<UUID, String> loadNicknamesByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<UUID> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        List<Talent> talents = talentMapper.selectBatchIds(distinct);
        if (talents == null || talents.isEmpty()) {
            return Map.of();
        }
        return talents.stream()
                .filter(talent -> talent.getId() != null && StringUtils.hasText(talent.getNickname()))
                .collect(Collectors.toMap(
                        Talent::getId,
                        Talent::getNickname,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    @Override
    public TalentReadDTO findOrCreateSampleTalent(String douyinUid, String nickname, Long fansCount) {
        if (!StringUtils.hasText(douyinUid)) {
            return null;
        }
        Talent existing = talentMapper.selectOne(new LambdaQueryWrapper<Talent>()
                .eq(Talent::getDouyinUid, douyinUid.trim())
                .last("LIMIT 1"));
        if (existing != null) {
            return toTalentRead(existing);
        }
        Talent talent = new Talent();
        talent.setId(UUID.randomUUID());
        talent.setDouyinUid(douyinUid.trim());
        talent.setNickname(nickname);
        talent.setFans(fansCount);
        talent.setStatus(1);
        talentMapper.insert(talent);
        return toTalentRead(talent);
    }

    @Override
    public boolean hasActiveClaim(UUID talentId, UUID userId) {
        if (talentId == null || userId == null) {
            return false;
        }
        return talentClaimMapper.findActiveByTalentAndUser(talentId, userId) != null;
    }

    @Override
    public boolean hasActiveClaimOwnerConflict(UUID talentId, UUID userId) {
        if (talentId == null || userId == null) {
            return false;
        }
        List<TalentClaim> activeClaims = talentClaimMapper.findActiveByTalentId(talentId);
        if (activeClaims == null || activeClaims.isEmpty()) {
            return false;
        }
        List<UUID> activeClaimUserIds = activeClaims.stream()
                .map(TalentClaim::getUserId)
                .filter(Objects::nonNull)
                .toList();
        return !activeClaimUserIds.isEmpty() && activeClaimUserIds.stream().noneMatch(userId::equals);
    }

    @Override
    public void writeBackClaimAddress(
            UUID channelUserId,
            UUID talentId,
            String recipientName,
            String recipientPhone,
            String recipientAddress) {
        if (channelUserId == null || talentId == null) {
            return;
        }
        if (!StringUtils.hasText(recipientName)
                && !StringUtils.hasText(recipientPhone)
                && !StringUtils.hasText(recipientAddress)) {
            return;
        }
        TalentClaim claim = talentClaimMapper.findActiveByTalentAndUser(talentId, channelUserId);
        if (claim == null) {
            return;
        }
        claim.setRecipientName(recipientName);
        claim.setRecipientPhone(recipientPhone);
        claim.setRecipientAddress(recipientAddress);
        talentClaimMapper.updateById(claim);
    }

    @Override
    public UUID resolveSampleOwnerForOrderCompletion(UUID attributedOwner, UUID talentId) {
        if (attributedOwner == null) {
            return null;
        }
        if (talentId == null) {
            return attributedOwner;
        }
        List<TalentClaim> activeClaims = talentClaimMapper.findActiveByTalentId(talentId);
        if (activeClaims == null || activeClaims.isEmpty()) {
            return attributedOwner;
        }
        boolean matchesClaimOwner = activeClaims.stream()
                .anyMatch(claim -> attributedOwner.equals(claim.getUserId()));
        if (matchesClaimOwner) {
            return attributedOwner;
        }
        return activeClaims.stream()
                .filter(claim -> claim.getUserId() != null)
                .max(Comparator.comparing(
                        TalentClaim::getClaimedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(TalentClaim::getUserId)
                .orElse(attributedOwner);
    }

    private static TalentReadDTO toTalentRead(Talent talent) {
        if (talent == null) {
            return null;
        }
        return new TalentReadDTO(
                talent.getId(),
                talent.getDouyinUid(),
                talent.getDouyinNo(),
                talent.getNickname(),
                talent.getFans(),
                talent.getStatus(),
                talent.getAvatarUrl(),
                talent.getMainCategory(),
                talent.getCategories(),
                talent.getIpLocation(),
                resolveWindowSales30d(talent.getRawPayload()));
    }

    private static Long resolveWindowSales30d(Map<String, Object> rawPayload) {
        if (rawPayload == null || rawPayload.isEmpty()) {
            return null;
        }
        for (String field : List.of("windowSales30d", "window_sales_30d", "showcaseSales30d")) {
            if (!rawPayload.containsKey(field)) {
                continue;
            }
            Object value = rawPayload.get(field);
            return value instanceof Number number ? toExactNonNegativeLong(number) : null;
        }
        return null;
    }

    private static Long toExactNonNegativeLong(Number number) {
        BigInteger integer;
        try {
            if (number instanceof BigInteger bigInteger) {
                integer = bigInteger;
            } else if (number instanceof BigDecimal bigDecimal) {
                integer = bigDecimal.toBigIntegerExact();
            } else if (number instanceof Byte
                    || number instanceof Short
                    || number instanceof Integer
                    || number instanceof Long) {
                integer = BigInteger.valueOf(number.longValue());
            } else if (number instanceof Double doubleValue) {
                if (!Double.isFinite(doubleValue)) {
                    return null;
                }
                integer = BigDecimal.valueOf(doubleValue).toBigIntegerExact();
            } else if (number instanceof Float floatValue) {
                if (!Float.isFinite(floatValue)) {
                    return null;
                }
                integer = new BigDecimal(Float.toString(floatValue)).toBigIntegerExact();
            } else {
                return null;
            }
        } catch (ArithmeticException exception) {
            return null;
        }
        if (integer.signum() < 0 || integer.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
            return null;
        }
        return integer.longValue();
    }
}
