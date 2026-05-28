package com.colonel.saas.dto.talent;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 达人资料解析响应 DTO。
 * <p>
 * 返回达人资料解析结果，包括是否成功、数据来源、同步状态、资料详情等。
 * 关联业务领域：达人域（Talent），对接抖音开放平台。
 * </p>
 */
@Data
@Builder
public class ResolveTalentProfileResponse {

    /** 解析是否成功 */
    private boolean success;
    /** 数据提供方标识（如 douyin-api） */
    private String provider;
    /** 同步状态（如 success、partial、failed） */
    private String syncStatus;
    /** 解析得到的达人资料负载 */
    private TalentProfilePayload profile;
    /** 平台返回但当前系统未支持的字段列表 */
    private List<String> unsupportedFields;
    /** 原始响应是否已保存到本地存储 */
    private boolean rawPayloadSaved;
    /** 数据来源描述（如 api、manual、cache） */
    private String dataSource;
    /** 同步错误码，解析失败时填充 */
    private String syncErrorCode;
    /** 同步错误信息，解析失败时填充 */
    private String syncErrorMessage;
}
