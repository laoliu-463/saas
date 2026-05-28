package com.colonel.saas.dto.talent;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 达人资料解析请求 DTO。
 * <p>
 * 用于通过抖音平台接口或手动方式解析并获取达人资料信息。
 * 支持传入抖音号、UID、主页链接等多种输入形式。
 * 关联业务领域：达人域（Talent），对接抖音开放平台。
 * </p>
 */
@Data
public class ResolveTalentProfileRequest {

    /** 达人标识输入，支持抖音号、UID、secUid、主页链接等，必填 */
    @NotBlank(message = "input 不能为空")
    private String input;

    /** 是否强制刷新缓存，从抖音平台重新拉取最新数据 */
    private Boolean forceRefresh;

    /** 是否为手动填充模式，当平台无法获取时使用手动录入 */
    private Boolean manualFill;

    /** 手动填充的资料数据，仅在 manualFill=true 时生效 */
    private Map<String, Object> manualPayload;
}
