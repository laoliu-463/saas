package com.colonel.saas.dto.talent;

/** 投诉创建输入；达人、商品和合作单均由服务端上下文解析。 */
public record TalentComplaintCreateRequest(String reason, String content) {
}
