package com.colonel.saas.domain.talent.facade;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.domain.talent.facade.dto.TalentReadDTO;
import com.colonel.saas.domain.talent.facade.dto.TalentShippingAddressDTO;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.entity.TalentClaim;
import com.colonel.saas.mapper.TalentClaimMapper;
import com.colonel.saas.mapper.TalentMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

    public LegacyTalentDomainFacade(TalentMapper talentMapper, TalentClaimMapper talentClaimMapper) {
        this.talentMapper = talentMapper;
        this.talentClaimMapper = talentClaimMapper;
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
    public TalentShippingAddressDTO findClaimShippingAddress(UUID channelUserId, UUID talentId) {
        if (channelUserId == null || talentId == null) {
            return TalentShippingAddressDTO.empty();
        }
        TalentClaim claim = talentClaimMapper.findActiveByTalentAndUser(talentId, channelUserId);
        if (claim == null) {
            return TalentShippingAddressDTO.empty();
        }
        return new TalentShippingAddressDTO(
                claim.getRecipientName(),
                claim.getRecipientPhone(),
                claim.getRecipientAddress());
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
                talent.getIpLocation());
    }
}
