package com.colonel.saas.domain.sample.policy;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.entity.SampleRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 合作申请备注的兼容读取与双写策略。
 */
@Component
public class SampleRemarkPolicy {

    public static final int MAX_LENGTH = 200;
    private static final String SPECIFICATION_KEY = "specification";
    private static final String APPLY_REASON_KEY = "applyReason";

    public String displayRemark(Map<String, Object> extraData, String legacyRemark) {
        String applyReason = readText(extraData, APPLY_REASON_KEY);
        if (StringUtils.hasText(applyReason)) {
            return normalize(applyReason);
        }
        String normalizedLegacy = normalize(legacyRemark);
        String specification = readText(extraData, SPECIFICATION_KEY);
        if (!StringUtils.hasText(normalizedLegacy) || !StringUtils.hasText(specification)) {
            return normalizedLegacy;
        }
        String prefix = "规格: " + specification.trim() + "；";
        if (normalizedLegacy.startsWith(prefix)) {
            return normalize(normalizedLegacy.substring(prefix.length()));
        }
        return normalizedLegacy;
    }

    public String apply(SampleRequest sample, String value) {
        String normalized = normalize(value);
        sample.setRemark(normalized);
        Map<String, Object> extra = sample.getExtraData() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(sample.getExtraData());
        extra.put(APPLY_REASON_KEY, normalized);
        sample.setExtraData(extra);
        return normalized;
    }

    public String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_LENGTH) {
            throw BusinessException.param("备注不能超过 200 个字符");
        }
        return normalized;
    }

    private String readText(Map<String, Object> extraData, String key) {
        if (extraData == null || extraData.isEmpty()) {
            return null;
        }
        Object value = extraData.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
