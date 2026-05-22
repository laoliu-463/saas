package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.common.exception.OptimisticLockSupport;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.PickSourceMapping;
import com.colonel.saas.mapper.PickSourceMappingMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class PickSourceMappingService {
    private static final Pattern SHORT_ID_PATTERN = Pattern.compile("([0-9A-Z]{8,10})");
    public static final String SOURCE_TYPE_PICK_SOURCE = "PICK_SOURCE";
    public static final String SOURCE_TYPE_NATIVE = "NATIVE";

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
        saveOrUpdate(
                userId,
                channelUserName,
                deptId,
                talentId,
                talentName,
                shortId,
                uuidSeed,
                pickSource,
                productId,
                activityId,
                sourceUrl,
                convertedUrl,
                promotionLinkId,
                null
        );
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
            UUID promotionLinkId,
            String scene) {
        saveOrUpdate(
                userId,
                channelUserName,
                deptId,
                talentId,
                talentName,
                shortId,
                uuidSeed,
                pickSource,
                productId,
                activityId,
                sourceUrl,
                convertedUrl,
                promotionLinkId,
                scene,
                shortId,
                null,
                SOURCE_TYPE_PICK_SOURCE
        );
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
            UUID promotionLinkId,
            String scene,
            String pickExtra) {
        saveOrUpdate(
                userId,
                channelUserName,
                deptId,
                talentId,
                talentName,
                shortId,
                uuidSeed,
                pickSource,
                productId,
                activityId,
                sourceUrl,
                convertedUrl,
                promotionLinkId,
                scene,
                pickExtra,
                null,
                SOURCE_TYPE_PICK_SOURCE
        );
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
            UUID promotionLinkId,
            String scene,
            String pickExtra,
            String colonelBuyinId) {
        saveOrUpdate(
                userId,
                channelUserName,
                deptId,
                talentId,
                talentName,
                shortId,
                uuidSeed,
                pickSource,
                productId,
                activityId,
                sourceUrl,
                convertedUrl,
                promotionLinkId,
                scene,
                pickExtra,
                colonelBuyinId,
                StringUtils.hasText(colonelBuyinId) ? SOURCE_TYPE_NATIVE : SOURCE_TYPE_PICK_SOURCE
        );
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
            UUID promotionLinkId,
            String scene,
            String pickExtra,
            String colonelBuyinId,
            String sourceType) {
        String resolvedSourceType = resolveSourceType(sourceType, colonelBuyinId);
        PickSourceMapping existing = findExistingMapping(
                userId,
                pickSource,
                productId,
                activityId,
                promotionLinkId,
                colonelBuyinId,
                resolvedSourceType
        );
        if (existing == null) {
            try {
                PickSourceMapping mapping = new PickSourceMapping();
                mapping.setId(UUID.randomUUID());
                mapping.setUserId(userId);
                mapping.setChannelUserName(channelUserName);
                mapping.setDeptId(deptId);
                mapping.setTalentId(talentId);
                mapping.setTalentName(talentName);
                mapping.setShortId(shortId);
                mapping.setUuidSeed(uuidSeed);
                mapping.setPickSource(pickSource);
                mapping.setColonelBuyinId(resolveColonelBuyinId(colonelBuyinId));
                mapping.setProductId(productId);
                mapping.setActivityId(activityId);
                mapping.setSourceUrl(sourceUrl);
                mapping.setConvertedUrl(convertedUrl);
                mapping.setPickExtra(resolvePickExtra(pickExtra));
                mapping.setPromotionLinkId(promotionLinkId);
                mapping.setScene(scene);
                mapping.setSourceType(resolvedSourceType);
                mapping.setValidFrom(LocalDateTime.now());
                mapping.setValidUntil(LocalDateTime.now().plusMonths(validMonths));
                mapping.setStatus(1);
                pickSourceMappingMapper.insert(mapping);
                logNativeAmbiguousIfNeeded(mapping);
                return;
            } catch (DuplicateKeyException ex) {
                log.debug("Concurrent insert detected for pickSource={}, attempting recovery", pickSource);
                existing = findExistingMapping(
                        userId,
                        pickSource,
                        productId,
                        activityId,
                        promotionLinkId,
                        colonelBuyinId,
                        resolvedSourceType
                );
                if (existing == null) {
                    throw ex;
                }
            }
        }
        PickSourceMapping update = buildUpdateEntity(
                existing,
                userId,
                channelUserName,
                deptId,
                talentId,
                talentName,
                shortId,
                uuidSeed,
                pickSource,
                colonelBuyinId,
                productId,
                activityId,
                sourceUrl,
                convertedUrl,
                promotionLinkId,
                scene,
                pickExtra,
                resolvedSourceType
        );
        try {
            persistPickSourceMapping(update);
            logNativeAmbiguousIfNeeded(materializeForLogging(existing, update));
        } catch (DuplicateKeyException ex) {
            PickSourceMapping recovered = recoverNativeConflict(
                    ex,
                    userId,
                    channelUserName,
                    deptId,
                    talentId,
                    talentName,
                    shortId,
                    uuidSeed,
                    pickSource,
                    colonelBuyinId,
                    productId,
                    activityId,
                    sourceUrl,
                    convertedUrl,
                    promotionLinkId,
                    scene,
                    pickExtra,
                    resolvedSourceType
            );
            if (recovered == null) {
                throw ex;
            }
            logNativeAmbiguousIfNeeded(recovered);
        }
    }

    private PickSourceMapping findExistingMapping(
            UUID userId,
            String pickSource,
            String productId,
            String activityId,
            UUID promotionLinkId,
            String colonelBuyinId,
            String sourceType) {
        if (promotionLinkId != null) {
            PickSourceMapping existingByPromotionLink = pickSourceMappingMapper.selectOne(new LambdaQueryWrapper<PickSourceMapping>()
                    .eq(PickSourceMapping::getPromotionLinkId, promotionLinkId)
                    .last("limit 1"));
            if (existingByPromotionLink != null) {
                return existingByPromotionLink;
            }
        }
        if (SOURCE_TYPE_NATIVE.equals(sourceType)
                && userId != null
                && StringUtils.hasText(colonelBuyinId)
                && StringUtils.hasText(productId)
                && StringUtils.hasText(activityId)) {
            PickSourceMapping existingByNativeComposite = pickSourceMappingMapper.selectOne(new LambdaQueryWrapper<PickSourceMapping>()
                    .eq(PickSourceMapping::getColonelBuyinId, resolveColonelBuyinId(colonelBuyinId))
                    .eq(PickSourceMapping::getProductId, productId)
                    .eq(PickSourceMapping::getActivityId, activityId)
                    .eq(PickSourceMapping::getUserId, userId)
                    .eq(PickSourceMapping::getSourceType, SOURCE_TYPE_NATIVE)
                    .last("limit 1"));
            if (existingByNativeComposite != null) {
                return existingByNativeComposite;
            }
        }
        if (userId != null && StringUtils.hasText(pickSource) && StringUtils.hasText(productId) && StringUtils.hasText(activityId)) {
            PickSourceMapping existingByComposite = pickSourceMappingMapper.selectOne(new LambdaQueryWrapper<PickSourceMapping>()
                    .eq(PickSourceMapping::getUserId, userId)
                    .eq(PickSourceMapping::getPickSource, pickSource)
                    .eq(PickSourceMapping::getProductId, productId)
                    .eq(PickSourceMapping::getActivityId, activityId)
                    .last("limit 1"));
            if (existingByComposite != null) {
                return existingByComposite;
            }
        }
        if (StringUtils.hasText(pickSource) && !StringUtils.hasText(productId) && !StringUtils.hasText(activityId)) {
            return pickSourceMappingMapper.selectOne(new LambdaQueryWrapper<PickSourceMapping>()
                    .eq(PickSourceMapping::getPickSource, pickSource)
                    .last("limit 1"));
        }
        return null;
    }

    private PickSourceMapping buildUpdateEntity(
            PickSourceMapping existing,
            UUID userId,
            String channelUserName,
            UUID deptId,
            String talentId,
            String talentName,
            String shortId,
            UUID uuidSeed,
            String pickSource,
            String colonelBuyinId,
            String productId,
            String activityId,
            String sourceUrl,
            String convertedUrl,
            UUID promotionLinkId,
            String scene,
            String pickExtra,
            String resolvedSourceType) {
        boolean preserveNativeIdentity = shouldPreserveNativeIdentity(
                existing,
                userId,
                colonelBuyinId,
                productId,
                activityId,
                resolvedSourceType
        );
        PickSourceMapping update = new PickSourceMapping();
        update.setId(existing.getId());
        update.setChannelUserName(channelUserName);
        update.setTalentId(talentId);
        update.setTalentName(talentName);
        update.setDeptId(deptId);
        update.setPickSource(pickSource);
        update.setSourceUrl(sourceUrl);
        update.setConvertedUrl(convertedUrl);
        update.setPickExtra(resolvePickExtra(pickExtra));
        update.setPromotionLinkId(promotionLinkId);
        update.setScene(scene);
        update.setValidUntil(LocalDateTime.now().plusMonths(validMonths));
        update.setStatus(1);
        if (!preserveNativeIdentity) {
            update.setShortId(shortId);
            update.setUuidSeed(uuidSeed);
            update.setUserId(userId);
            update.setColonelBuyinId(resolveColonelBuyinId(colonelBuyinId));
            update.setProductId(productId);
            update.setActivityId(activityId);
            update.setSourceType(resolvedSourceType);
        }
        return update;
    }

    private PickSourceMapping materializeForLogging(PickSourceMapping existing, PickSourceMapping update) {
        PickSourceMapping materialized = new PickSourceMapping();
        materialized.setId(existing.getId());
        materialized.setUserId(update.getUserId() != null ? update.getUserId() : existing.getUserId());
        materialized.setChannelUserName(update.getChannelUserName() != null ? update.getChannelUserName() : existing.getChannelUserName());
        materialized.setTalentId(update.getTalentId() != null ? update.getTalentId() : existing.getTalentId());
        materialized.setTalentName(update.getTalentName() != null ? update.getTalentName() : existing.getTalentName());
        materialized.setShortId(update.getShortId() != null ? update.getShortId() : existing.getShortId());
        materialized.setUuidSeed(update.getUuidSeed() != null ? update.getUuidSeed() : existing.getUuidSeed());
        materialized.setDeptId(update.getDeptId() != null ? update.getDeptId() : existing.getDeptId());
        materialized.setPickSource(update.getPickSource() != null ? update.getPickSource() : existing.getPickSource());
        materialized.setColonelBuyinId(update.getColonelBuyinId() != null ? update.getColonelBuyinId() : existing.getColonelBuyinId());
        materialized.setProductId(update.getProductId() != null ? update.getProductId() : existing.getProductId());
        materialized.setActivityId(update.getActivityId() != null ? update.getActivityId() : existing.getActivityId());
        materialized.setSourceUrl(update.getSourceUrl() != null ? update.getSourceUrl() : existing.getSourceUrl());
        materialized.setConvertedUrl(update.getConvertedUrl() != null ? update.getConvertedUrl() : existing.getConvertedUrl());
        materialized.setPickExtra(update.getPickExtra() != null ? update.getPickExtra() : existing.getPickExtra());
        materialized.setPromotionLinkId(update.getPromotionLinkId() != null ? update.getPromotionLinkId() : existing.getPromotionLinkId());
        materialized.setScene(update.getScene() != null ? update.getScene() : existing.getScene());
        materialized.setSourceType(update.getSourceType() != null ? update.getSourceType() : existing.getSourceType());
        materialized.setValidFrom(existing.getValidFrom());
        materialized.setValidUntil(update.getValidUntil() != null ? update.getValidUntil() : existing.getValidUntil());
        materialized.setStatus(update.getStatus() != null ? update.getStatus() : existing.getStatus());
        materialized.setDeleted(existing.getDeleted());
        return materialized;
    }

    private PickSourceMapping recoverNativeConflict(
            DuplicateKeyException ex,
            UUID userId,
            String channelUserName,
            UUID deptId,
            String talentId,
            String talentName,
            String shortId,
            UUID uuidSeed,
            String pickSource,
            String colonelBuyinId,
            String productId,
            String activityId,
            String sourceUrl,
            String convertedUrl,
            UUID promotionLinkId,
            String scene,
            String pickExtra,
            String resolvedSourceType) {
        if (!SOURCE_TYPE_NATIVE.equals(resolvedSourceType)
                || userId == null
                || !StringUtils.hasText(colonelBuyinId)
                || !StringUtils.hasText(productId)
                || !StringUtils.hasText(activityId)) {
            return null;
        }
        PickSourceMapping nativeExisting = pickSourceMappingMapper.selectOne(new LambdaQueryWrapper<PickSourceMapping>()
                .eq(PickSourceMapping::getColonelBuyinId, resolveColonelBuyinId(colonelBuyinId))
                .eq(PickSourceMapping::getProductId, productId)
                .eq(PickSourceMapping::getActivityId, activityId)
                .eq(PickSourceMapping::getUserId, userId)
                .eq(PickSourceMapping::getSourceType, SOURCE_TYPE_NATIVE)
                .last("limit 1"));
        if (nativeExisting == null) {
            nativeExisting = pickSourceMappingMapper.selectOne(new LambdaQueryWrapper<PickSourceMapping>()
                    .eq(PickSourceMapping::getColonelBuyinId, resolveColonelBuyinId(colonelBuyinId))
                    .eq(PickSourceMapping::getProductId, productId)
                    .eq(PickSourceMapping::getActivityId, activityId)
                    .eq(PickSourceMapping::getSourceType, SOURCE_TYPE_NATIVE)
                    .last("limit 1"));
            if (nativeExisting == null) {
                return null;
            }
            log.warn("Native mapping identity conflict recovered without owner overwrite, activityId={}, productId={}, colonelBuyinId={}, existingUserId={}, incomingUserId={}",
                    activityId,
                    productId,
                    resolveColonelBuyinId(colonelBuyinId),
                    nativeExisting.getUserId(),
                    userId);
            PickSourceMapping patch = buildNativeIdentityConflictPatch(nativeExisting);
            persistPickSourceMapping(patch);
            return materializeForLogging(nativeExisting, patch);
        }
        PickSourceMapping patch = buildUpdateEntity(
                nativeExisting,
                userId,
                channelUserName,
                deptId,
                talentId,
                talentName,
                shortId,
                uuidSeed,
                pickSource,
                colonelBuyinId,
                productId,
                activityId,
                sourceUrl,
                convertedUrl,
                promotionLinkId,
                scene,
                pickExtra,
                resolvedSourceType
        );
        persistPickSourceMapping(patch);
        return materializeForLogging(nativeExisting, patch);
    }

    private void persistPickSourceMapping(PickSourceMapping mapping) {
        OptimisticLockSupport.requireUpdated(pickSourceMappingMapper.updateById(mapping));
    }

    private PickSourceMapping buildNativeIdentityConflictPatch(PickSourceMapping existing) {
        PickSourceMapping patch = new PickSourceMapping();
        patch.setId(existing.getId());
        patch.setValidUntil(LocalDateTime.now().plusMonths(validMonths));
        patch.setStatus(1);
        return patch;
    }

    private boolean shouldPreserveNativeIdentity(
            PickSourceMapping existing,
            UUID userId,
            String colonelBuyinId,
            String productId,
            String activityId,
            String resolvedSourceType) {
        return existing != null
                && SOURCE_TYPE_NATIVE.equals(resolvedSourceType)
                && SOURCE_TYPE_NATIVE.equals(existing.getSourceType())
                && userId != null
                && userId.equals(existing.getUserId())
                && resolveColonelBuyinId(colonelBuyinId) != null
                && resolveColonelBuyinId(colonelBuyinId).equals(existing.getColonelBuyinId())
                && StringUtils.hasText(productId)
                && productId.equals(existing.getProductId())
                && StringUtils.hasText(activityId)
                && activityId.equals(existing.getActivityId());
    }

    private String resolveSourceType(String sourceType, String colonelBuyinId) {
        if (StringUtils.hasText(sourceType)) {
            return sourceType.trim().toUpperCase();
        }
        return StringUtils.hasText(colonelBuyinId) ? SOURCE_TYPE_NATIVE : SOURCE_TYPE_PICK_SOURCE;
    }

    private void logNativeAmbiguousIfNeeded(PickSourceMapping mapping) {
        if (mapping == null
                || !SOURCE_TYPE_NATIVE.equals(mapping.getSourceType())
                || !StringUtils.hasText(mapping.getColonelBuyinId())
                || !StringUtils.hasText(mapping.getActivityId())
                || !StringUtils.hasText(mapping.getProductId())) {
            return;
        }
        List<PickSourceMapping> mappings = pickSourceMappingMapper.selectList(new LambdaQueryWrapper<PickSourceMapping>()
                .eq(PickSourceMapping::getSourceType, SOURCE_TYPE_NATIVE)
                .eq(PickSourceMapping::getColonelBuyinId, mapping.getColonelBuyinId())
                .eq(PickSourceMapping::getActivityId, mapping.getActivityId())
                .eq(PickSourceMapping::getProductId, mapping.getProductId())
                .eq(PickSourceMapping::getStatus, 1));
        if (mappings == null || mappings.isEmpty()) {
            return;
        }
        long distinctUsers = mappings.stream()
                .map(PickSourceMapping::getUserId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count();
        if (distinctUsers > 1) {
            log.warn("Native mapping ambiguous for activityId={}, productId={}, colonelBuyinId={}, distinctUsers={}",
                    mapping.getActivityId(),
                    mapping.getProductId(),
                    mapping.getColonelBuyinId(),
                    distinctUsers);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void ensureFromOrder(ColonelsettlementOrder order) {
        if (order == null
                || !StringUtils.hasText(order.getAttributionStatus())
                || !"ATTRIBUTED".equalsIgnoreCase(order.getAttributionStatus())
                || order.getUserId() == null) {
            return;
        }
        if (StringUtils.hasText(order.getPickSource())) {
            ensurePickSourceMappingFromOrder(order);
            return;
        }
        if (order.getColonelBuyinId() != null
                && StringUtils.hasText(order.getProductId())
                && StringUtils.hasText(order.getActivityId())) {
            ensureNativeMappingFromOrder(order);
        }
    }

    private void ensurePickSourceMappingFromOrder(ColonelsettlementOrder order) {
        PickSourceMapping existingByPickSource = pickSourceMappingMapper.selectOne(new LambdaQueryWrapper<PickSourceMapping>()
                .eq(PickSourceMapping::getPickSource, order.getPickSource())
                .last("limit 1"));
        if (existingByPickSource != null) {
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
        mapping.setId(UUID.randomUUID());
        mapping.setShortId(shortId);
        mapping.setPickSource(order.getPickSource());
        mapping.setPickExtra(resolveOrderPickExtra(order));
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
        } catch (DuplicateKeyException ex) {
            log.debug("Concurrent insert detected for pickSource={}, skipping", order.getPickSource());
        }
    }

    private void ensureNativeMappingFromOrder(ColonelsettlementOrder order) {
        String colonelBuyinId = String.valueOf(order.getColonelBuyinId());
        String productId = order.getProductId().trim();
        String activityId = order.getActivityId().trim();
        PickSourceMapping existing = pickSourceMappingMapper.selectOne(new LambdaQueryWrapper<PickSourceMapping>()
                .eq(PickSourceMapping::getSourceType, SOURCE_TYPE_NATIVE)
                .eq(PickSourceMapping::getColonelBuyinId, colonelBuyinId)
                .eq(PickSourceMapping::getProductId, productId)
                .eq(PickSourceMapping::getActivityId, activityId)
                .eq(PickSourceMapping::getUserId, order.getUserId())
                .last("limit 1"));
        if (existing != null) {
            return;
        }

        String seedMaterial = colonelBuyinId + ":" + productId + ":" + activityId + ":" + order.getUserId();
        UUID uuidSeed = UUID.nameUUIDFromBytes(seedMaterial.getBytes(StandardCharsets.UTF_8));
        String shortId = "N" + uuidSeed.toString().replace("-", "").substring(0, 9).toUpperCase(Locale.ROOT);
        String syntheticPickSource = "colonel_native_" + shortId;
        PickSourceMapping existingBySyntheticSource = pickSourceMappingMapper.selectOne(new LambdaQueryWrapper<PickSourceMapping>()
                .eq(PickSourceMapping::getPickSource, syntheticPickSource)
                .last("limit 1"));
        if (existingBySyntheticSource != null) {
            return;
        }

        PickSourceMapping mapping = new PickSourceMapping();
        mapping.setId(UUID.randomUUID());
        mapping.setSourceType(SOURCE_TYPE_NATIVE);
        mapping.setColonelBuyinId(colonelBuyinId);
        mapping.setShortId(shortId);
        mapping.setPickSource(syntheticPickSource);
        mapping.setPickExtra(resolveOrderPickExtra(order));
        mapping.setUserId(order.getUserId());
        mapping.setDeptId(order.getDeptId());
        mapping.setProductId(productId);
        mapping.setActivityId(activityId);
        mapping.setSourceUrl(syntheticPickSource);
        mapping.setConvertedUrl(syntheticPickSource);
        mapping.setValidFrom(LocalDateTime.now());
        mapping.setValidUntil(LocalDateTime.now().plusMonths(validMonths));
        mapping.setStatus(1);
        mapping.setUuidSeed(uuidSeed);
        try {
            pickSourceMappingMapper.insert(mapping);
        } catch (DuplicateKeyException ex) {
            log.debug("Concurrent native mapping insert detected for activityId={}, productId={}, colonelBuyinId={}, userId={}, skipping",
                    activityId, productId, colonelBuyinId, order.getUserId());
        }
    }

    private String resolvePickExtra(String pickExtra) {
        if (StringUtils.hasText(pickExtra)) {
            return pickExtra.trim();
        }
        return null;
    }

    private String resolveColonelBuyinId(String colonelBuyinId) {
        if (StringUtils.hasText(colonelBuyinId)) {
            return colonelBuyinId.trim();
        }
        return null;
    }

    private String resolveOrderPickExtra(ColonelsettlementOrder order) {
        if (order != null && order.getExtraData() != null) {
            Object pickExtra = order.getExtraData().get("pick_extra");
            if (pickExtra != null && StringUtils.hasText(String.valueOf(pickExtra))) {
                return String.valueOf(pickExtra).trim();
            }
        }
        return null;
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
