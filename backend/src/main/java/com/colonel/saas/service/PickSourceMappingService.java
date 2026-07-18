package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.common.exception.OptimisticLockSupport;
import com.colonel.saas.domain.product.facade.dto.PickSourceAttributionMappingDTO;
import com.colonel.saas.domain.product.facade.dto.PickSourceMappingReadDTO;
import com.colonel.saas.domain.shared.attribution.AttributionOwnerType;
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
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 精选联盟商品转链映射管理服务 (god service - 边缘服务, 不再 DDD 切片).
 *
 * <p><strong>当前状态 (2026-07-14):</strong></p>
 * <ul>
 *   <li>1043 行 / 15 public method (含 5 个 saveOrUpdate 重载)</li>
 *   <li>5+ 跨域 caller (AttributionService / OrderSyncPersistenceService / ProductService / PromotionApi / DouyinContractFixtureProvider / TestDouyinOrderGateway)</li>
 *   <li>不切理由: 跨域 caller 多 + 14 参 saveOrUpdate 重载, 切片必牵连 caller</li>
 * </ul>
 * 精选联盟商品转链映射管理服务。
 * <p>
 * 维护 {@link PickSourceMapping} 记录，支持两种来源类型：
 * <ul>
 *   <li>{@link #SOURCE_TYPE_PICK_SOURCE} — 标准精选联盟转链（pickSource 唯一标识）</li>
 *   <li>{@link #SOURCE_TYPE_NATIVE} — 原生订单映射（以 colonelBuyinId + productId + activityId 为复合键）</li>
 * </ul>
 * 写入采用 upsert 策略：先按多级优先级查找已有记录，命中则更新；未命中则插入。
 * 插入和更新阶段均处理 {@link DuplicateKeyException} 并发冲突，确保高并发场景下的数据一致性。
 * </p>
 */
@Slf4j
@Service
public class PickSourceMappingService {
    /** 短 ID 正则，匹配 8-10 位大写字母数字组合 */
    private static final Pattern SHORT_ID_PATTERN = Pattern.compile("([0-9A-Z]{8,10})");

    /** 来源类型：精选联盟转链 */
    public static final String SOURCE_TYPE_PICK_SOURCE = "PICK_SOURCE";

    /** 来源类型：原生订单（无 pickSource，以 colonelBuyinId 标识） */
    public static final String SOURCE_TYPE_NATIVE = "NATIVE";

    private final PickSourceMappingMapper pickSourceMappingMapper;

    /** 映射记录有效期（月），通过 {@code pick.source.valid-months} 配置，默认 3 */
    private final int validMonths;

    public PickSourceMappingService(
            PickSourceMappingMapper pickSourceMappingMapper,
            @Value("${pick.source.valid-months:3}") int validMonths) {
        this.pickSourceMappingMapper = pickSourceMappingMapper;
        this.validMonths = validMonths;
    }

    /**
     * 查询最新活跃转链映射。
     *
     * <p>保留原网关夹具读取口径：{@code status=1}，按 {@code update_time} 倒序取第一条。</p>
     */
    public PickSourceMappingReadDTO findLatestActiveMapping() {
        PickSourceMapping mapping = pickSourceMappingMapper.selectOne(new LambdaQueryWrapper<PickSourceMapping>()
                .eq(PickSourceMapping::getStatus, 1)
                .orderByDesc(PickSourceMapping::getUpdateTime)
                .last("limit 1"));
        return toReadDTO(mapping);
    }

    /**
     * 订单归因：按 pick_source / pick_extra 读取活跃映射。
     *
     * <p>保持原归因查询顺序：pickSource 精确匹配 -> pickExtra 精确匹配 -> pickExtra 后 20 位 shortId 匹配。</p>
     */
    public PickSourceAttributionMappingDTO findActiveAttributionMapping(String pickSource, String pickExtra) {
        if (StringUtils.hasText(pickSource)) {
            PickSourceMapping byPickSource = pickSourceMappingMapper.selectOne(new LambdaQueryWrapper<PickSourceMapping>()
                    .eq(PickSourceMapping::getPickSource, pickSource)
                    .eq(PickSourceMapping::getStatus, 1)
                    .last("limit 1"));
            if (byPickSource != null) {
                return toAttributionDTO(byPickSource);
            }
        }
        if (StringUtils.hasText(pickExtra)) {
            PickSourceMapping byPickExtra = pickSourceMappingMapper.selectOne(new LambdaQueryWrapper<PickSourceMapping>()
                    .eq(PickSourceMapping::getPickExtra, pickExtra)
                    .eq(PickSourceMapping::getStatus, 1)
                    .last("limit 1"));
            if (byPickExtra != null) {
                return toAttributionDTO(byPickExtra);
            }
            String normalized = pickExtra.length() > 20 ? pickExtra.substring(pickExtra.length() - 20) : pickExtra;
            PickSourceMapping byShortId = pickSourceMappingMapper.selectOne(new LambdaQueryWrapper<PickSourceMapping>()
                    .eq(PickSourceMapping::getShortId, normalized)
                    .eq(PickSourceMapping::getStatus, 1)
                    .last("limit 1"));
            if (byShortId != null) {
                return toAttributionDTO(byShortId);
            }
        }
        return null;
    }

    /** 订单归因：原生团长精确映射。 */
    public List<PickSourceAttributionMappingDTO> findNativeAttributionMappings(
            String colonelBuyinId,
            String activityId,
            String productId) {
        return toAttributionDTOs(pickSourceMappingMapper.selectList(new LambdaQueryWrapper<PickSourceMapping>()
                .eq(PickSourceMapping::getColonelBuyinId, colonelBuyinId)
                .eq(PickSourceMapping::getActivityId, activityId)
                .eq(PickSourceMapping::getProductId, productId)
                .eq(PickSourceMapping::getSourceType, SOURCE_TYPE_NATIVE)
                .eq(PickSourceMapping::getStatus, 1)
                .orderByDesc(PickSourceMapping::getUpdateTime)));
    }

    /** 订单归因：活动商品维度原生映射回退。 */
    public List<PickSourceAttributionMappingDTO> findNativeAttributionMappingsByActivityProduct(
            String activityId,
            String productId) {
        return toAttributionDTOs(pickSourceMappingMapper.selectList(new LambdaQueryWrapper<PickSourceMapping>()
                .eq(PickSourceMapping::getActivityId, activityId)
                .eq(PickSourceMapping::getProductId, productId)
                .eq(PickSourceMapping::getSourceType, SOURCE_TYPE_NATIVE)
                .eq(PickSourceMapping::getStatus, 1)
                .orderByDesc(PickSourceMapping::getUpdateTime)));
    }

    /** 订单归因：仅按团长 buyin_id 的原生映射通用回退。 */
    public List<PickSourceAttributionMappingDTO> findNativeAttributionMappingsByColonelBuyinId(String colonelBuyinId) {
        return toAttributionDTOs(pickSourceMappingMapper.selectList(new LambdaQueryWrapper<PickSourceMapping>()
                .eq(PickSourceMapping::getColonelBuyinId, colonelBuyinId)
                .eq(PickSourceMapping::getSourceType, SOURCE_TYPE_NATIVE)
                .eq(PickSourceMapping::getStatus, 1)
                .orderByDesc(PickSourceMapping::getUpdateTime)));
    }

    /**
     * 保存或更新转链映射（最简参数，scene 默认为 null）。
     *
     * @param userId          操作用户 ID
     * @param channelUserName 渠道用户名称
     * @param deptId          部门 ID
     * @param talentId        达人 ID
     * @param talentName      达人名称
     * @param shortId         短 ID（8-10 位字母数字）
     * @param uuidSeed        UUID 种子，用于生成确定性 UUID
     * @param pickSource      精选联盟转链标识
     * @param productId       商品 ID
     * @param activityId      活动 ID
     * @param sourceUrl       原始链接
     * @param convertedUrl    转链后链接
     * @param promotionLinkId 推广链接 ID（可为 null）
     */
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

    /**
     * 保存或更新转链映射（带场景参数，pickExtra 默认为 shortId）。
     *
     * @param scene 场景标识，如直播、短视频等（可为 null）
     */
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

    /**
     * 保存或更新转链映射（带 pickExtra，来源类型默认为 PICK_SOURCE）。
     *
     * @param pickExtra 附加的转链扩展信息（可为 null）
     */
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

    /**
     * 保存或更新转链映射（带 colonelBuyinId，自动推断来源类型）。
     * <p>
     * 当 colonelBuyinId 非空时来源类型为 NATIVE，否则为 PICK_SOURCE。
     *
     * @param colonelBuyinId 团长买入 ID（可为 null），存在时标识 NATIVE 来源类型
     */
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

    /**
     * 保存或更新转链映射（全参数版本，其他重载最终委托到此方法）。
     * <p>
     * 执行流程：
     * <ol>
     *   <li>解析来源类型（sourceType 优先，否则根据 colonelBuyinId 推断）</li>
     *   <li>按多级优先级查找已有映射：promotionLinkId → NATIVE 复合键 → 通用复合键 → pickSource 唯一</li>
     *   <li>未找到则插入新记录；插入遇到 {@link DuplicateKeyException} 时重试查找并降级为更新</li>
     *   <li>找到已有记录则构建更新实体（NATIVE 身份保护逻辑见 {@link #shouldPreserveNativeIdentity}）</li>
     *   <li>更新遇到 {@link DuplicateKeyException} 时尝试 NATIVE 冲突恢复（见 {@link #recoverNativeConflict}）</li>
     * </ol>
     *
     * @param sourceType 来源类型，{@code null} 时根据 colonelBuyinId 自动推断
     */
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
        saveOrUpdate(
                userId, channelUserName, deptId, talentId, talentName,
                shortId, uuidSeed, pickSource, productId, activityId,
                sourceUrl, convertedUrl, promotionLinkId, scene, pickExtra,
                colonelBuyinId, sourceType, null);
    }

    /** 保存或更新转链映射，并固化创建链接时的归属维度。 */
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
            String sourceType,
            String attributionOwnerType) {
        String resolvedSourceType = resolveSourceType(sourceType, colonelBuyinId);
        String resolvedAttributionOwnerType = resolveAttributionOwnerType(attributionOwnerType);
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
                mapping.setAttributionOwnerType(resolvedAttributionOwnerType);
                mapping.setAttributionSnapshot(attributionSnapshot(
                        userId, deptId, productId, activityId, talentId,
                        resolvedSourceType, resolvedAttributionOwnerType));
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
                resolvedSourceType,
                resolvedAttributionOwnerType
        );
        update.setAttributionSnapshot(attributionSnapshot(
                userId, deptId, productId, activityId, talentId,
                resolvedSourceType, resolvedAttributionOwnerType));
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
                    resolvedSourceType,
                    resolvedAttributionOwnerType
            );
            if (recovered == null) {
                throw ex;
            }
            logNativeAmbiguousIfNeeded(recovered);
        }
    }

    private static Map<String, Object> attributionSnapshot(
            UUID userId,
            UUID deptId,
            String productId,
            String activityId,
            String talentId,
            String sourceType,
            String attributionOwnerType) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("ownerUserId", userId == null ? null : userId.toString());
        snapshot.put("ownerDeptId", deptId == null ? null : deptId.toString());
        snapshot.put("productId", productId);
        snapshot.put("activityId", activityId);
        snapshot.put("talentId", talentId);
        snapshot.put("sourceType", sourceType);
        snapshot.put("attributionOwnerType", attributionOwnerType);
        snapshot.put("recordedAt", LocalDateTime.now().toString());
        return snapshot;
    }

    /**
     * 按多级优先级查找已有的转链映射记录。
     * <p>
     * 查找策略（命中即返回）：
     * <ol>
     *   <li>promotionLinkId 精确匹配（最高优先级）</li>
     *   <li>NATIVE 来源：colonelBuyinId + productId + activityId + userId + sourceType=NATIVE 复合匹配</li>
     *   <li>通用复合键：userId + pickSource + productId + activityId 匹配</li>
     *   <li>仅 pickSource 匹配（仅当 productId 和 activityId 均为空时）</li>
     * </ol>
     *
     * @return 找到的映射记录，未找到返回 {@code null}
     */
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

    /**
     * 基于已有记录构建更新实体。
     * <p>
     * 当 {@link #shouldPreserveNativeIdentity} 判定需要保留 NATIVE 身份时，
     * 短 ID、uuidSeed、userId、colonelBuyinId、productId、activityId、sourceType 等身份字段不会被覆盖，
     * 仅更新渠道、达人、链接等附属信息和有效期。
     *
     * @param existing           已有的映射记录
     * @param resolvedSourceType 已解析的来源类型
     * @return 可直接用于 {@code updateById} 的更新实体
     */
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
            String resolvedSourceType,
            String resolvedAttributionOwnerType) {
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
        if (existing.getAttributionOwnerType() == null) {
            update.setAttributionOwnerType(resolvedAttributionOwnerType);
        }
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

    /**
     * 将已有记录和更新片段合并为完整实体，用于日志记录和 {@link #logNativeAmbiguousIfNeeded} 检查。
     * <p>
     * 更新字段非 null 时取更新值，否则保留已有记录的值。
     */
    private PickSourceMapping materializeForLogging(PickSourceMapping existing, PickSourceMapping update) {
        PickSourceMapping materialized = new PickSourceMapping();
        materialized.setId(existing.getId());
        materialized.setUserId(update.getUserId() != null ? update.getUserId() : existing.getUserId());
        materialized.setChannelUserName(update.getChannelUserName() != null ? update.getChannelUserName() : existing.getChannelUserName());
        materialized.setAttributionOwnerType(update.getAttributionOwnerType() != null
                ? update.getAttributionOwnerType()
                : existing.getAttributionOwnerType());
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

    /**
     * NATIVE 来源类型更新阶段的 {@link DuplicateKeyException} 恢复处理。
     * <p>
     * 当更新 NATIVE 映射触发唯一键冲突时，尝试按复合键（colonelBuyinId + productId + activityId）重新查找记录：
     * <ul>
     *   <li>若找到的记录属于当前 userId：正常更新</li>
     *   <li>若找到的记录属于其他 userId（身份冲突）：仅续期，不覆盖身份字段，
     *       并记录 {@code Native mapping identity conflict recovered} 警告日志</li>
     * </ul>
     *
     * @return 恢复后的合并实体（用于日志），恢复失败返回 {@code null}（调用方应重新抛出原始异常）
     */
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
            String resolvedSourceType,
            String resolvedAttributionOwnerType) {
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
                resolvedSourceType,
                resolvedAttributionOwnerType
        );
        persistPickSourceMapping(patch);
        return materializeForLogging(nativeExisting, patch);
    }

    /** 更新映射记录并要求至少一行受影响（乐观锁），否则抛出异常。 */
    private void persistPickSourceMapping(PickSourceMapping mapping) {
        OptimisticLockSupport.requireUpdated(pickSourceMappingMapper.updateById(mapping));
    }

    /**
     * 构建 NATIVE 身份冲突时的最小补丁：仅续期和重置状态，不覆盖身份字段。
     */
    private PickSourceMapping buildNativeIdentityConflictPatch(PickSourceMapping existing) {
        PickSourceMapping patch = new PickSourceMapping();
        patch.setId(existing.getId());
        patch.setValidUntil(LocalDateTime.now().plusMonths(validMonths));
        patch.setStatus(1);
        return patch;
    }

    /**
     * 判断更新时是否应保留已有 NATIVE 映射的身份字段。
     * <p>
     * 当已有记录和新数据均为 NATIVE 来源、且 userId/colonelBuyinId/productId/activityId 完全一致时，
     * 返回 {@code true}，阻止身份字段被覆盖（防止并发更新导致身份漂移）。
     */
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

    /**
     * 解析来源类型：显式指定则大写标准化，否则根据 colonelBuyinId 是否存在推断。
     */
    private String resolveSourceType(String sourceType, String colonelBuyinId) {
        if (StringUtils.hasText(sourceType)) {
            return sourceType.trim().toUpperCase();
        }
        return StringUtils.hasText(colonelBuyinId) ? SOURCE_TYPE_NATIVE : SOURCE_TYPE_PICK_SOURCE;
    }

    private String resolveAttributionOwnerType(String attributionOwnerType) {
        AttributionOwnerType ownerType = AttributionOwnerType.parseNullable(attributionOwnerType);
        return ownerType == null ? null : ownerType.name();
    }

    /**
     * 检查 NATIVE 映射是否存在歧义（同一 colonelBuyinId + productId + activityId 对应多个不同 userId）。
     * 存在歧义时输出警告日志，便于运营排查数据归属问题。
     */
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

    /**
     * 从已归因订单出发，确保对应转链映射记录存在。
     * <p>
     * 仅处理归因状态为 {@code ATTRIBUTED} 且 userId 非空的订单。
     * 根据订单是否携带 pickSource 分派到不同的映射创建逻辑：
     * <ul>
     *   <li>有 pickSource → {@link #ensurePickSourceMappingFromOrder}</li>
     *   <li>无 pickSource 但有 colonelBuyinId + productId + activityId → {@link #ensureNativeMappingFromOrder}</li>
     * </ul>
     */
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

    /**
     * 从订单的 pickSource 创建映射记录（PICK_SOURCE 类型）。
     * <p>
     * 若 pickSource 或从中提取的 shortId 已存在对应映射则跳过；
     * 插入遇到 {@link DuplicateKeyException} 时静默忽略（并发创建场景安全）。
     */
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

    /**
     * 从订单创建原生映射记录（NATIVE 类型，无 pickSource）。
     * <p>
     * 使用 colonelBuyinId + productId + activityId + userId 生成确定性 UUID 种子和短 ID，
     * 合成 {@code colonel_native_{shortId}} 形式的 pickSource。
     * 存在映射时跳过；插入遇到 {@link DuplicateKeyException} 时静默忽略。
     */
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

    /** 标准化 pickExtra：非空时 trim，空则返回 null。 */
    private String resolvePickExtra(String pickExtra) {
        if (StringUtils.hasText(pickExtra)) {
            return pickExtra.trim();
        }
        return null;
    }

    /** 标准化 colonelBuyinId：非空时 trim，空则返回 null。 */
    private String resolveColonelBuyinId(String colonelBuyinId) {
        if (StringUtils.hasText(colonelBuyinId)) {
            return colonelBuyinId.trim();
        }
        return null;
    }

    /** 从订单的 extraData map 中提取 pick_extra 字段，不存在或空白时返回 null。 */
    private String resolveOrderPickExtra(ColonelsettlementOrder order) {
        if (order != null && order.getExtraData() != null) {
            Object pickExtra = order.getExtraData().get("pick_extra");
            if (pickExtra != null && StringUtils.hasText(String.valueOf(pickExtra))) {
                return String.valueOf(pickExtra).trim();
            }
        }
        return null;
    }

    /**
     * 查询指定 colonelBuyinId 下所有有效映射关联的 productId 集合。
     *
     * @param colonelBuyinId 机构买手 ID，空白时返回空集合
     * @return 关联的 productId 集合（去重、保序），无匹配时返回空集合
     */
    public Set<String> listProductIdsByColonelBuyinId(String colonelBuyinId) {
        String resolved = resolveColonelBuyinId(colonelBuyinId);
        if (!StringUtils.hasText(resolved)) {
            return Set.of();
        }
        return pickSourceMappingMapper.selectList(new LambdaQueryWrapper<PickSourceMapping>()
                        .select(PickSourceMapping::getProductId)
                        .eq(PickSourceMapping::getDeleted, 0)
                        .eq(PickSourceMapping::getColonelBuyinId, resolved)
                        .isNotNull(PickSourceMapping::getProductId))
                .stream()
                .map(PickSourceMapping::getProductId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private PickSourceMappingReadDTO toReadDTO(PickSourceMapping mapping) {
        if (mapping == null) {
            return null;
        }
        return new PickSourceMappingReadDTO(
                mapping.getShortId(),
                mapping.getProductId(),
                mapping.getActivityId(),
                mapping.getPickSource(),
                mapping.getPickExtra(),
                mapping.getTalentId(),
                mapping.getTalentName());
    }

    private List<PickSourceAttributionMappingDTO> toAttributionDTOs(List<PickSourceMapping> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            return List.of();
        }
        return mappings.stream()
                .map(PickSourceMappingService::toAttributionDTO)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private static PickSourceAttributionMappingDTO toAttributionDTO(PickSourceMapping mapping) {
        if (mapping == null) {
            return null;
        }
        return new PickSourceAttributionMappingDTO(
                mapping.getUserId(),
                mapping.getDeptId(),
                mapping.getActivityId(),
                mapping.getProductId(),
                mapping.getColonelBuyinId(),
                mapping.getSourceType(),
                mapping.getCreateTime(),
                mapping.getUpdateTime());
    }

    /**
     * 从 pickSource 字符串中提取短 ID（8–10 位字母数字）。
     * <p>
     * 若整个 pickSource 本身即为 10 位以内的合法短 ID 则直接返回；
     * 否则使用 {@link #SHORT_ID_PATTERN} 正则提取首个匹配子串。
     *
     * @param pickSource 原始 pickSource 值
     * @return 提取到的短 ID（大写），无法提取时返回 null
     */
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
