package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.common.enums.ProductBizStatus;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.OptimisticLockSupport;
import com.colonel.saas.entity.ProductOperationLog;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.mapper.ProductOperationLogMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 商品寄样规则读写服务。
 *
 * <p>寄样规则属于商品运营状态的审核扩展数据，暂不新增表字段，使用现有
 * {@code product_operation_state.audit_payload} 持久化。写入时只更新寄样字段，
 * 保留同一商品已有的审核补充信息。</p>
 */
@Service
public class ProductSampleSettingService {

    private static final String OPERATION_TYPE = "SAMPLE_SETTING";
    private static final String DEFAULT_SAMPLE_SETTING_REMARK = "更新寄样设置";

    private final ProductSnapshotMapper snapshotMapper;
    private final ProductOperationStateMapper operationStateMapper;
    private final ProductOperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;

    public ProductSampleSettingService(
            ProductSnapshotMapper snapshotMapper,
            ProductOperationStateMapper operationStateMapper,
            ProductOperationLogMapper operationLogMapper,
            ObjectMapper objectMapper) {
        this.snapshotMapper = snapshotMapper;
        this.operationStateMapper = operationStateMapper;
        this.operationLogMapper = operationLogMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 按商品关系 ID 查询寄样设置。
     *
     * @param relationId {@code product_snapshot.id}，即商品库前端使用的关系 ID
     * @return 当前审核扩展数据；尚未设置时返回空对象
     */
    public Map<String, Object> get(UUID relationId) {
        ProductSnapshot snapshot = requireSnapshot(relationId);
        ProductOperationState state = findState(snapshot);
        return readAuditPayload(state == null ? null : state.getAuditPayload());
    }

    /**
     * 保存寄样设置，并记录商品操作日志。
     *
     * <p>该接口只修改寄样规则，不改变商品审核状态、上架状态或业务状态。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> update(
            UUID relationId,
            Map<String, Object> request,
            UUID operatorId,
            UUID operatorDeptId) {
        if (relationId == null) {
            throw BusinessException.param("商品关系 ID 不能为空");
        }
        if (request == null || request.isEmpty()) {
            throw BusinessException.param("寄样设置不能为空");
        }

        ProductSnapshot snapshot = requireSnapshot(relationId);
        ProductOperationState state = findState(snapshot);
        boolean newState = state == null;
        String beforeStatus = newState ? ProductBizStatus.PENDING_AUDIT.name() : state.getBizStatus();
        if (state == null) {
            state = new ProductOperationState();
            state.setId(UUID.randomUUID());
            state.setActivityId(snapshot.getActivityId());
            state.setProductId(snapshot.getProductId());
            state.setBizStatus(ProductBizStatus.PENDING_AUDIT.name());
            state.setAuditStatus(1);
        }

        Map<String, Object> merged = new LinkedHashMap<>(readAuditPayload(state.getAuditPayload()));
        applySupportFreeSample(request, merged);
        boolean thresholdEnabled = applyThresholdSwitch(request, merged);
        applyThresholdValues(request, merged, thresholdEnabled);
        applySampleQuantity(request, merged);

        state.setAuditPayload(writeJson(merged));
        state.setLastOperationAt(LocalDateTime.now());
        if (newState) {
            operationStateMapper.insert(state);
        } else {
            OptimisticLockSupport.requireUpdated(
                    operationStateMapper.updateById(state),
                    "商品寄样设置已被其他人修改，请刷新后重试");
        }

        ProductOperationLog log = new ProductOperationLog();
        log.setId(UUID.randomUUID());
        log.setActivityId(snapshot.getActivityId());
        log.setProductId(snapshot.getProductId());
        log.setOperationType(OPERATION_TYPE);
        log.setBeforeStatus(beforeStatus);
        log.setAfterStatus(state.getBizStatus());
        log.setSuccess(true);
        log.setOperatorId(operatorId);
        log.setOperatorDeptId(operatorDeptId);
        log.setOperationPayload(writeJson(request));
        log.setOperationRemark(DEFAULT_SAMPLE_SETTING_REMARK);
        operationLogMapper.insert(log);
        return merged;
    }

    private ProductSnapshot requireSnapshot(UUID relationId) {
        if (relationId == null) {
            throw BusinessException.param("商品关系 ID 不能为空");
        }
        ProductSnapshot snapshot = snapshotMapper.selectById(relationId);
        if (snapshot == null) {
            throw BusinessException.notFound("商品不存在: " + relationId);
        }
        if (!StringUtils.hasText(snapshot.getActivityId()) || !StringUtils.hasText(snapshot.getProductId())) {
            throw BusinessException.conflict("商品缺少活动或商品关联，无法保存寄样设置");
        }
        return snapshot;
    }

    private ProductOperationState findState(ProductSnapshot snapshot) {
        return operationStateMapper.selectOne(new LambdaQueryWrapper<ProductOperationState>()
                .eq(ProductOperationState::getActivityId, snapshot.getActivityId())
                .eq(ProductOperationState::getProductId, snapshot.getProductId()));
    }

    private void applySupportFreeSample(Map<String, Object> request, Map<String, Object> merged) {
        Boolean supportFreeSample = optionalBoolean(request, "supportFreeSample");
        if (supportFreeSample == null) {
            supportFreeSample = optionalBoolean(request, "freeSample");
        }
        if (supportFreeSample == null && containsAny(request, "sampleType")) {
            Object rawSampleType = request.get("sampleType");
            if (rawSampleType != null && StringUtils.hasText(String.valueOf(rawSampleType))) {
                String sampleType = String.valueOf(rawSampleType).trim().toUpperCase();
                if (!"FREE".equals(sampleType) && !"PAID".equals(sampleType)) {
                    throw BusinessException.param("寄样类型只能是 FREE 或 PAID");
                }
                supportFreeSample = "FREE".equals(sampleType);
            }
        }
        if (supportFreeSample != null) {
            merged.put("supportFreeSample", supportFreeSample);
            merged.put("freeSample", supportFreeSample);
            merged.put("sampleType", supportFreeSample ? "FREE" : "PAID");
        }
        if (containsAny(request, "allowSample")) {
            merged.put("allowSample", requireBoolean(request.get("allowSample"), "是否允许寄样"));
        }
    }

    /**
     * @return 当前是否启用门槛；没有传开关时保留旧值，传入门槛数值则视为启用
     */
    private boolean applyThresholdSwitch(Map<String, Object> request, Map<String, Object> merged) {
        Boolean enabled = optionalBoolean(request, "hasSampleThreshold");
        if (enabled == null && containsAny(request,
                "minWindowSales30d", "windowSales30dMin", "minSales30d", "sampleThresholdSales",
                "salesRequirement30d", "minFans", "fansMin", "minTalentLevel", "sampleThresholdLevel",
                "talentLevelRequirement")) {
            enabled = true;
        }
        if (enabled != null) {
            merged.put("hasSampleThreshold", enabled);
            if (!enabled) {
                removeAll(merged,
                        "minWindowSales30d", "windowSales30dMin", "minSales30d", "sampleThresholdSales",
                        "salesRequirement30d", "minFans", "fansMin", "minTalentLevel", "sampleThresholdLevel",
                        "talentLevelRequirement");
            }
        }
        if (enabled != null) {
            return enabled;
        }
        return !Boolean.FALSE.equals(merged.get("hasSampleThreshold"));
    }

    private void applyThresholdValues(Map<String, Object> request, Map<String, Object> merged, boolean enabled) {
        if (!enabled) {
            return;
        }
        applyOptionalNumber(request, merged, "minWindowSales30d", 0L, "windowSales30dMin");
        applyOptionalNumber(request, merged, "minSales30d", 0L, "sampleThresholdSales", "salesRequirement30d");
        applyOptionalNumber(request, merged, "minFans", 0L, "fansMin");
        applyOptionalNumber(request, merged, "minTalentLevel", 0L, "sampleThresholdLevel", "talentLevelRequirement");
    }

    private void applySampleQuantity(Map<String, Object> request, Map<String, Object> merged) {
        if (containsAny(request, "sampleBoxCount", "sampleBoxes")) {
            long sampleBoxCount = requireNumber(valueOf(request, "sampleBoxCount", "sampleBoxes"),
                    "样品盒数", 1L);
            merged.put("sampleBoxCount", sampleBoxCount);
            merged.put("sampleBoxes", sampleBoxCount);
        }
        if (containsAny(request, "sampleQuantity", "quantity")) {
            long sampleQuantity = requireNumber(valueOf(request, "sampleQuantity", "quantity"),
                    "样品数量", 1L);
            merged.put("sampleQuantity", sampleQuantity);
            merged.put("quantity", sampleQuantity);
        }
    }

    private void applyOptionalNumber(
            Map<String, Object> request,
            Map<String, Object> merged,
            String canonicalKey,
            long minimum,
            String... aliases) {
        String[] keys = new String[aliases.length + 1];
        keys[0] = canonicalKey;
        System.arraycopy(aliases, 0, keys, 1, aliases.length);
        if (!containsAny(request, keys)) {
            return;
        }
        Object raw = valueOf(request, keys);
        if (raw == null || (raw instanceof String text && !StringUtils.hasText(text))) {
            removeAll(merged, keys);
            return;
        }
        long normalized = requireNumber(raw, displayName(canonicalKey), minimum);
        merged.put(canonicalKey, normalized);
        for (String alias : aliases) {
            merged.put(alias, normalized);
        }
    }

    private Boolean optionalBoolean(Map<String, Object> values, String key) {
        if (!containsAny(values, key) || values.get(key) == null) {
            return null;
        }
        return requireBoolean(values.get(key), key);
    }

    private boolean requireBoolean(Object raw, String fieldName) {
        if (raw instanceof Boolean value) {
            return value;
        }
        if (raw instanceof Number value && (value.intValue() == 0 || value.intValue() == 1)) {
            return value.intValue() == 1;
        }
        if (raw instanceof String text) {
            if ("true".equalsIgnoreCase(text.trim()) || "1".equals(text.trim())) {
                return true;
            }
            if ("false".equalsIgnoreCase(text.trim()) || "0".equals(text.trim())) {
                return false;
            }
        }
        throw BusinessException.param(fieldName + "必须是布尔值");
    }

    private long requireNumber(Object raw, String fieldName, long minimum) {
        if (raw == null) {
            throw BusinessException.param(fieldName + "不能为空");
        }
        try {
            BigDecimal decimal = new BigDecimal(String.valueOf(raw).trim());
            if (decimal.stripTrailingZeros().scale() > 0) {
                throw BusinessException.param(fieldName + "必须是整数");
            }
            long value = decimal.longValueExact();
            if (value < minimum) {
                throw BusinessException.param(fieldName + "不能小于 " + minimum);
            }
            return value;
        } catch (NumberFormatException | ArithmeticException ex) {
            throw BusinessException.param(fieldName + "必须是合法整数", ex);
        }
    }

    private Map<String, Object> readAuditPayload(String rawPayload) {
        if (!StringUtils.hasText(rawPayload)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(
                    rawPayload,
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    });
            return payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
        } catch (Exception ex) {
            throw BusinessException.business("商品寄样设置数据损坏，请联系管理员", ex);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw BusinessException.business("寄样设置保存失败，无法生成审计数据", ex);
        }
    }

    private boolean containsAny(Map<String, Object> values, String... keys) {
        return Arrays.stream(keys).anyMatch(values::containsKey);
    }

    private Object valueOf(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            if (values.containsKey(key)) {
                return values.get(key);
            }
        }
        return null;
    }

    private void removeAll(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            values.remove(key);
        }
    }

    private String displayName(String canonicalKey) {
        return switch (canonicalKey) {
            case "minWindowSales30d" -> "近30天橱窗销量";
            case "minSales30d" -> "近30天销售额";
            case "minFans" -> "粉丝数";
            case "minTalentLevel" -> "达人带货等级";
            default -> canonicalKey;
        };
    }
}
