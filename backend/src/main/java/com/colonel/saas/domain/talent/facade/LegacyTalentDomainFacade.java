package com.colonel.saas.domain.talent.facade;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.domain.talent.facade.dto.TalentReadDTO;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.mapper.TalentMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
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

    public LegacyTalentDomainFacade(TalentMapper talentMapper) {
        this.talentMapper = talentMapper;
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
                talent.getStatus());
    }
}
