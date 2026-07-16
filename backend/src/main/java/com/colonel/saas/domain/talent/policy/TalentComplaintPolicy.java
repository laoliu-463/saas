package com.colonel.saas.domain.talent.policy;

import com.colonel.saas.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Set;

/** 达人投诉原因与正文边界。 */
@Component
public class TalentComplaintPolicy {

    public static final String REPEATED_NO_FULFILLMENT = "REPEATED_NO_FULFILLMENT";
    public static final String LOW_PRICE_RESALE = "LOW_PRICE_RESALE";
    public static final String OTHER = "OTHER";
    public static final int MAX_CONTENT_CODE_POINTS = 200;

    private static final Set<String> ALLOWED_REASONS = Set.of(
            REPEATED_NO_FULFILLMENT,
            LOW_PRICE_RESALE,
            OTHER);

    public ValidatedComplaint validate(String reason, String content) {
        String normalizedReason = reason == null ? null : reason.trim();
        if (!ALLOWED_REASONS.contains(normalizedReason)) {
            throw BusinessException.param("投诉原因不合法");
        }
        String normalizedContent = content == null ? null : content.trim();
        if (normalizedContent != null && normalizedContent.isEmpty()) {
            normalizedContent = null;
        }
        if (OTHER.equals(normalizedReason) && normalizedContent == null) {
            throw BusinessException.param("选择其他原因时必须填写投诉内容");
        }
        if (normalizedContent != null
                && normalizedContent.codePointCount(0, normalizedContent.length())
                > MAX_CONTENT_CODE_POINTS) {
            throw BusinessException.param("投诉内容最多 200 字");
        }
        return new ValidatedComplaint(normalizedReason, normalizedContent);
    }

    public record ValidatedComplaint(String reasonCode, String content) {
    }
}
