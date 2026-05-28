package com.colonel.saas.common.enums;

/**
 * 达人输入类型枚举，描述运营人员在录入达人信息时使用的标识方式。
 *
 * <p>当运营人员在前端页面批量导入或单个添加达人时，
 * 系统需要根据输入的标识类型来判断如何解析和查询达人信息。
 * 不同的输入类型对应不同的查询策略和去重逻辑。</p>
 *
 * <h3>枚举值说明</h3>
 * <ul>
 *   <li>{@link #DOUYIN_NO} — 抖音号（用户自定义的唯一标识，可能被修改）</li>
 *   <li>{@link #PROFILE_URL} — 达人主页链接，需要从 URL 中解析出唯一标识</li>
 *   <li>{@link #SHARE_LINK} — 达人分享链接，需要解析短链接并提取达人信息</li>
 *   <li>{@link #UID} — 达人用户 ID（数字型唯一标识）</li>
 *   <li>{@link #SEC_UID} — 达人加密用户 ID（抖音用于隐私保护的加密标识）</li>
 *   <li>{@link #DOUYIN_UID} — 抖音系统内部 UID（与 UID 类似但来源不同）</li>
 *   <li>{@link #UNKNOWN} — 无法识别的输入格式，将触发解析错误提示</li>
 * </ul>
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li>达人批量导入时标识每条记录的输入格式</li>
 *   <li>达人查询时根据标识类型选择对应的 API 查询方式</li>
 *   <li>达人去重时根据标识类型匹配已有记录</li>
 * </ul>
 */
public enum TalentInputType {
    /** 抖音号（用户自定义的唯一标识，可能被修改） */
    DOUYIN_NO,
    /** 达人主页链接（如 https://www.douyin.com/user/xxx） */
    PROFILE_URL,
    /** 达人分享链接（短链接，需要解析后提取达人标识） */
    SHARE_LINK,
    /** 达人数字型用户 ID */
    UID,
    /** 达人加密用户 ID（抖音隐私保护标识，稳定性最高） */
    SEC_UID,
    /** 抖音系统内部 UID */
    DOUYIN_UID,
    /** 无法识别的输入格式，将触发解析错误提示 */
    UNKNOWN
}

