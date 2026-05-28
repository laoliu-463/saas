package com.colonel.saas.service.talent.profile;

/**
 * 达人资料字段名常量注册表 —— 集中定义所有达人资料同步（Profile Sync）场景中使用的字段名称。
 *
 * <p><b>职责：</b></p>
 * <ul>
 *   <li>为达人资料采集链路中所有 Provider 和 Service 提供统一的字段名常量，
 *       避免魔法字符串散布于各处</li>
 *   <li>常量值与 {@link com.colonel.saas.entity.Talent} 实体属性名保持一致（驼峰命名）</li>
 *   <li>同时兼容外部 API 返回的驼峰和下划线两种 JSON key 风格（各 Provider 在正则/解析中自行处理）</li>
 *   <li>标记通常不支持自动采集的字段（如 {@code TALENT_LEVEL}、{@code SALES_30D}），
 *       作为 {@link TalentProfileResult#DEFAULT_UNSUPPORTED} 的数据来源</li>
 * </ul>
 *
 * <p><b>在架构中的角色：</b>作为工具类（Utility Class），被所有 {@link TalentProfileProvider}
 * 实现类和 {@link TalentProfileSyncService} 引用，确保字段名在整条采集链路中保持一致。</p>
 *
 * <p><b>业务域：</b>达人域 / 达人资料采集子域</p>
 *
 * @see TalentProfileProvider
 * @see TalentProfileResult
 * @see com.colonel.saas.entity.Talent
 */
public final class TalentProfileFieldNames {

    /** 抖音账号（如 "douyin_user123"），与 {@code Talent.douyinAccount} 对应 */
    public static final String DOUYIN_ACCOUNT = "douyinAccount";

    /** 达人 UID（数字 ID），与 {@code Talent.talentUid} 对应 */
    public static final String TALENT_UID = "talentUid";

    /** 安全 UID（secUid），抖音平台用于隐私保护的加密标识，与 {@code Talent.secUid} 对应 */
    public static final String SEC_UID = "secUid";

    /** 达人昵称，与 {@code Talent.nickname} 对应 */
    public static final String NICKNAME = "nickname";

    /** 达人头像 URL，与 {@code Talent.avatarUrl} 对应 */
    public static final String AVATAR_URL = "avatarUrl";

    /** 粉丝数，与 {@code Talent.fans} 对应 */
    public static final String FANS_COUNT = "fansCount";

    /** 累计获赞数，与 {@code Talent.likesCount} 对应 */
    public static final String LIKE_COUNT = "likeCount";

    /** 关注数，与 {@code Talent.followingCount} 对应 */
    public static final String FOLLOWING_COUNT = "followingCount";

    /** 作品数，与 {@code Talent.worksCount} 对应 */
    public static final String WORKS_COUNT = "worksCount";

    /** IP 属地（如"广东深圳"），与 {@code Talent.ipLocation} 对应 */
    public static final String IP_LOCATION = "ipLocation";

    /** 达人等级，通常不支持自动采集，需手动补录或第三方接口 */
    public static final String TALENT_LEVEL = "talentLevel";

    /** 30 天销售额，通常不支持自动采集，需手动补录或第三方接口 */
    public static final String SALES_30D = "sales30d";

    /**
     * 私有构造器 —— 阻止实例化工具类。
     */
    private TalentProfileFieldNames() {
    }
}
