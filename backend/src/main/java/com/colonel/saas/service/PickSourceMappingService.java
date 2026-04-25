package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.PickSourceMapping;
import com.colonel.saas.mapper.PickSourceMappingMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PickSourceMappingService {
    private static final Pattern SHORT_ID_PATTERN = Pattern.compile("([0-9A-Z]{8,10})");

    private final PickSourceMappingMapper pickSourceMappingMapper;
    private final int validMonths;

    public PickSourceMappingService(
            PickSourceMappingMapper pickSourceMappingMapper,
            @Value("${pick.source.valid-months:3}") int validMonths) {
        this.pickSourceMappingMapper = pickSourceMappingMapper;
        this.validMonths = validMonths;
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveOrUpdate(
            UUID userId,
            String channelUserName,
            UUID deptId,
            String talentId,
            String talentName,
            String shortId,
            UUID uuidSeed,
            String pickSource,
            String productId,
            String activityId,
            String sourceUrl,
            String convertedUrl,
            UUID promotionLinkId) {
        PickSourceMapping existing = pickSourceMappingMapper.selectOne(new LambdaQueryWrapper<PickSourceMapping>()
                .eq(PickSourceMapping::getPickSource, pickSource)
                .last("limit 1"));
        if (existing == null) {
            try {
                PickSourceMapping mapping = new PickSourceMapping();
                mapping.setUserId(userId);
                mapping.setChannelUserName(channelUserName);
                mapping.setDeptId(deptId);
                mapping.setTalentId(talentId);
                mapping.setTalentName(talentName);
                mapping.setShortId(shortId);
                mapping.setUuidSeed(uuidSeed);
                mapping.setPickSource(pickSource);
                mapping.setProductId(productId);
                mapping.setActivityId(activityId);
                mapping.setSourceUrl(sourceUrl);
                mapping.setConvertedUrl(convertedUrl);
                mapping.setPickExtra(shortId);
                mapping.setPromotionLinkId(promotionLinkId);
                mapping.setValidFrom(LocalDateTime.now());
                mapping.setValidUntil(LocalDateTime.now().plusMonths(validMonths));
                mapping.setStatus(1);
                pickSourceMappingMapper.insert(mapping);
                return;
            } catch (DuplicateKeyException ignore) {
                existing = pickSourceMappingMapper.selectOne(new LambdaQueryWrapper<PickSourceMapping>()
                        .eq(PickSourceMapping::getPickSource, pickSource)
                        .last("limit 1"));
                if (existing == null) {
                    throw ignore;
                }
            }
        }
        existing.setUserId(userId);
        existing.setChannelUserName(channelUserName);
        existing.setTalentId(talentId);
        existing.setTalentName(talentName);
        existing.setShortId(shortId);
        existing.setUuidSeed(uuidSeed);
        existing.setDeptId(deptId);
        existing.setProductId(productId);
        existing.setActivityId(activityId);
        existing.setSourceUrl(sourceUrl);
        existing.setConvertedUrl(convertedUrl);
        existing.setPickExtra(shortId);
        existing.setPromotionLinkId(promotionLinkId);
        existing.setValidUntil(LocalDateTime.now().plusMonths(validMonths));
        existing.setStatus(1);
        pickSourceMappingMapper.updateById(existing);
    }

    @Transactional(rollbackFor = Exception.class)
    public void ensureFromOrder(ColonelsettlementOrder order) {
        if (order == null || !StringUtils.hasText(order.getPickSource())) {
            return;
        }
        String shortId = extractShortId(order.getPickSource());
        if (!StringUtils.hasText(shortId)) {
            return;
        }
        PickSourceMapping existing = pickSourceMappingMapper.selectOne(new LambdaQueryWrapper<PickSourceMapping>()
                .eq(PickSourceMapping::getShortId, shortId)
                .last("limit 1"));
        if (existing != null) {
            return;
        }
        PickSourceMapping mapping = new PickSourceMapping();
        mapping.setShortId(shortId);
        mapping.setPickSource(order.getPickSource());
        mapping.setPickExtra(shortId);
        mapping.setUserId(order.getUserId());
        mapping.setDeptId(order.getDeptId());
        mapping.setProductId(order.getProductId());
        mapping.setSourceUrl(order.getPickSource());
        mapping.setConvertedUrl(order.getPickSource());
        mapping.setValidFrom(LocalDateTime.now());
        mapping.setValidUntil(LocalDateTime.now().plusMonths(validMonths));
        mapping.setStatus(1);
        mapping.setUuidSeed(UUID.nameUUIDFromBytes(shortId.getBytes(StandardCharsets.UTF_8)));
        try {
            pickSourceMappingMapper.insert(mapping);
        } catch (DuplicateKeyException ignore) {
            // concurrent insert is acceptable
        }
    }

    private String extractShortId(String pickSource) {
        if (!StringUtils.hasText(pickSource)) {
            return null;
        }
        String normalized = pickSource.trim().toUpperCase();
        if (normalized.length() <= 10 && normalized.matches("^[0-9A-Z]+$")) {
            return normalized;
        }
        Matcher matcher = SHORT_ID_PATTERN.matcher(normalized);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
