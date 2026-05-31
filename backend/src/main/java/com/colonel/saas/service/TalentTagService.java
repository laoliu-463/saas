package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.entity.TalentClaim;
import com.colonel.saas.entity.TalentTag;
import com.colonel.saas.entity.TalentTagRelation;
import com.colonel.saas.mapper.TalentClaimMapper;
import com.colonel.saas.mapper.TalentTagMapper;
import com.colonel.saas.mapper.TalentTagRelationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TalentTagService {

    public static final int MAX_TAGS_PER_TALENT = 3;
    private static final String TAG_TYPE_CLAIM = "CLAIM";
    private static final int CLAIM_STATUS_ACTIVE = 1;

    private final TalentTagMapper talentTagMapper;
    private final TalentTagRelationMapper talentTagRelationMapper;
    private final TalentClaimMapper talentClaimMapper;
    private final com.colonel.saas.mapper.TalentMapper talentMapper;

    public TalentTagService(
            TalentTagMapper talentTagMapper,
            TalentTagRelationMapper talentTagRelationMapper,
            TalentClaimMapper talentClaimMapper,
            com.colonel.saas.mapper.TalentMapper talentMapper) {
        this.talentTagMapper = talentTagMapper;
        this.talentTagRelationMapper = talentTagRelationMapper;
        this.talentClaimMapper = talentClaimMapper;
        this.talentMapper = talentMapper;
    }

    public List<String> listTagNames(UUID talentId) {
        if (talentId == null) {
            return List.of();
        }
        List<TalentTagRelation> relations = talentTagRelationMapper.selectList(
                new LambdaQueryWrapper<TalentTagRelation>()
                        .eq(TalentTagRelation::getTalentId, talentId)
                        .orderByAsc(TalentTagRelation::getCreateTime));
        if (relations.isEmpty()) {
            return List.of();
        }
        Set<UUID> tagIds = relations.stream()
                .map(TalentTagRelation::getTagId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (tagIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> tagNameById = talentTagMapper.selectBatchIds(tagIds).stream()
                .filter(tag -> tag.getId() != null && StringUtils.hasText(tag.getTagName()))
                .collect(Collectors.toMap(TalentTag::getId, TalentTag::getTagName, (left, right) -> left));
        List<String> names = new ArrayList<>();
        for (TalentTagRelation relation : relations) {
            String name = tagNameById.get(relation.getTagId());
            if (StringUtils.hasText(name)) {
                names.add(name.trim());
            }
        }
        return names;
    }

    public Map<UUID, List<String>> listTagNamesByTalentIds(Set<UUID> talentIds) {
        if (talentIds == null || talentIds.isEmpty()) {
            return Map.of();
        }
        List<TalentTagRelation> relations = talentTagRelationMapper.selectList(
                new LambdaQueryWrapper<TalentTagRelation>()
                        .in(TalentTagRelation::getTalentId, talentIds)
                        .orderByAsc(TalentTagRelation::getCreateTime));
        if (relations.isEmpty()) {
            return Map.of();
        }
        Set<UUID> tagIds = relations.stream()
                .map(TalentTagRelation::getTagId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> tagNameById = tagIds.isEmpty()
                ? Map.of()
                : talentTagMapper.selectBatchIds(tagIds).stream()
                        .filter(tag -> tag.getId() != null && StringUtils.hasText(tag.getTagName()))
                        .collect(Collectors.toMap(TalentTag::getId, TalentTag::getTagName, (left, right) -> left));

        Map<UUID, List<String>> result = new java.util.HashMap<>();
        for (TalentTagRelation relation : relations) {
            String name = tagNameById.get(relation.getTagId());
            if (!StringUtils.hasText(name) || relation.getTalentId() == null) {
                continue;
            }
            result.computeIfAbsent(relation.getTalentId(), ignored -> new ArrayList<>()).add(name.trim());
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<String> replaceTags(UUID talentId, UUID userId, List<String> rawTags, boolean admin) {
        if (talentId == null) {
            throw new BusinessException("达人不存在");
        }
        if (userId == null) {
            throw new BusinessException("缺少登录用户");
        }
        assertCanEditTags(talentId, userId, admin);

        Talent talent = talentMapper.selectById(talentId);
        if (talent == null) {
            throw new BusinessException("达人不存在");
        }
        return replaceTags(talent, userId, rawTags, admin);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<String> replaceTags(Talent talent, UUID userId, List<String> rawTags, boolean admin) {
        if (talent == null || talent.getId() == null) {
            throw new BusinessException("达人不存在");
        }
        if (userId == null) {
            throw new BusinessException("缺少登录用户");
        }
        assertCanEditTags(talent.getId(), userId, admin);

        List<String> normalized = normalizeTags(rawTags);
        if (normalized.size() > MAX_TAGS_PER_TALENT) {
            throw new BusinessException("达人标签最多" + MAX_TAGS_PER_TALENT + "个");
        }

        List<TalentTagRelation> existingRelations = talentTagRelationMapper.selectList(
                new LambdaQueryWrapper<TalentTagRelation>()
                        .eq(TalentTagRelation::getTalentId, talent.getId()));
        for (TalentTagRelation relation : existingRelations) {
            talentTagRelationMapper.deleteById(relation.getId());
        }

        for (String tagName : normalized) {
            TalentTag tag = findOrCreateTag(tagName);
            TalentTagRelation relation = new TalentTagRelation();
            relation.setId(UUID.randomUUID());
            relation.setTalentId(talent.getId());
            relation.setTagId(tag.getId());
            relation.setCreateUserId(userId);
            talentTagRelationMapper.insert(relation);
        }
        return normalized;
    }

    private void assertCanEditTags(UUID talentId, UUID userId, boolean admin) {
        if (admin) {
            return;
        }
        TalentClaim activeClaim = talentClaimMapper.findActiveByTalentAndUser(talentId, userId);
        if (activeClaim == null || activeClaim.getStatus() == null || activeClaim.getStatus() != CLAIM_STATUS_ACTIVE) {
            throw new BusinessException("仅当前认领人可维护达人标签");
        }
    }

    private List<String> normalizeTags(List<String> rawTags) {
        if (rawTags == null || rawTags.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String raw : rawTags) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            String tag = raw.trim();
            if (tag.length() > 50) {
                throw new BusinessException("单个标签最多50个字符");
            }
            normalized.add(tag);
            if (normalized.size() > MAX_TAGS_PER_TALENT) {
                throw new BusinessException("达人标签最多" + MAX_TAGS_PER_TALENT + "个");
            }
        }
        return List.copyOf(normalized);
    }

    private TalentTag findOrCreateTag(String tagName) {
        TalentTag existing = talentTagMapper.selectOne(
                new LambdaQueryWrapper<TalentTag>()
                        .eq(TalentTag::getTagName, tagName)
                        .eq(TalentTag::getTagType, TAG_TYPE_CLAIM)
                        .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        TalentTag tag = new TalentTag();
        tag.setId(UUID.randomUUID());
        tag.setTagName(tagName);
        tag.setTagType(TAG_TYPE_CLAIM);
        talentTagMapper.insert(tag);
        return tag;
    }
}