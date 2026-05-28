package com.colonel.saas.service.talent;

import com.colonel.saas.common.enums.TalentInputType;
import lombok.Builder;
import lombok.Data;

/**
 * 达人输入解析结果 —— 将用户原始输入文本解析为结构化的达人标识信息。
 *
 * <p><b>职责：</b></p>
 * <ul>
 *   <li>封装 {@link TalentInputParser} 对用户输入文本的解析结果</li>
 *   <li>存储达人抖音号、UID、secUid、主页链接等多种标识方式</li>
 *   <li>记录原始输入类型（抖音号/链接/UID 等），供下游采集链路识别</li>
 * </ul>
 *
 * <p><b>在架构中的角色：</b>作为达人资料采集链路的第一步产物，
 * 由 {@link TalentInputParser} 生成，被 {@link com.colonel.saas.service.talent.profile.TalentProfileQuery}
 * 消费，最终传递给各个 {@code TalentProfileProvider} 用于定位达人。</p>
 *
 * <p><b>业务域：</b>达人域 / 达人资料采集子域</p>
 *
 * @see TalentInputParser
 * @see com.colonel.saas.service.talent.profile.TalentProfileQuery
 */
@Data
@Builder
public class TalentInputParseResult {

    /** 输入类型枚举，标识用户原始输入的形式（如抖音号、主页链接、UID 等） */
    private TalentInputType inputType;

    /** 用户提交的原始未处理输入文本 */
    private String rawInput;

    /** 解析出的抖音号（如 "douyin123"），用于按抖音号查询达人 */
    private String douyinNo;

    /** 解析出的达人 UID（数字 ID），用于 API 调用时的达人定位 */
    private String uid;

    /**
     * 解析出的安全 UID（secUid），抖音平台用于隐私保护的加密标识。
     * 在公开页面爬取和 API 调用中均可用于唯一标识达人。
     */
    private String secUid;

    /** 解析出的达人主页完整 URL，用于公开页面爬取等场景 */
    private String profileUrl;

    /** 解析出的抖音内部 UID（douyinUid），与 {@link #uid} 类似但可能是另一种格式 */
    private String douyinUid;
}

