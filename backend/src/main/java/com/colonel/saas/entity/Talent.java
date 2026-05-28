package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.base.VersionedEntity;
import com.colonel.saas.common.typehandler.JsonbListTypeHandler;
import com.colonel.saas.common.typehandler.JsonbTypeHandler;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 达人实体。
 * <p>
 * 对应数据库表：{@code talent}，记录抖音平台达人的核心资料、社交数据、标签和运营状态。
 * 达人是业绩域和寄样域的关键业务对象：订单通过达人关联渠道归属，寄样请求围绕达人发起。
 * 达人资料支持多数据源同步（爬虫、抖音 API），通过 enrich/sync 状态机管理资料完整度。
 * 继承 {@link com.colonel.saas.common.base.VersionedEntity}，拥有乐观锁支持。
 * </p>
 *
 * @see TalentClaim 达人认领记录
 * @see TalentEnrichTask 达人资料补全任务
 * @see TalentFieldSource 达人字段来源追踪
 * @see TalentFollowRecord 达人跟进记录
 * @see TalentProfileSyncLog 达人资料同步日志
 * @see SampleRequest 寄样请求，围绕达人发起
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "talent", autoResultMap = true)
public class Talent extends VersionedEntity {

    /**
     * 抖音 UID
     * <p>对应数据库列：{@code douyin_uid}，抖音平台分配给达人的唯一标识</p>
     */
    @TableField("douyin_uid")
    private String douyinUid;

    /**
     * 抖音号
     * <p>对应数据库列：{@code douyin_no}，达人设置的抖音号（用户可见的短 ID）</p>
     */
    @TableField("douyin_no")
    private String douyinNo;

    /**
     * 用户 UID
     * <p>对应数据库列：{@code uid}，抖音平台的数字用户 ID</p>
     */
    @TableField("uid")
    private String uid;

    /**
     * 安全 UID
     * <p>对应数据库列：{@code sec_uid}，抖音平台的安全用户标识，
     * 用于 API 调用中的达人定位</p>
     */
    @TableField("sec_uid")
    private String secUid;

    /**
     * 主页链接
     * <p>对应数据库列：{@code profile_url}，达人抖音主页的完整 URL</p>
     */
    @TableField("profile_url")
    private String profileUrl;

    /**
     * 昵称
     * <p>达人抖音昵称，用于前端展示</p>
     */
    private String nickname;

    /**
     * 粉丝数
     * <p>对应数据库列：{@code fans_count}，达人的抖音粉丝总数。
     * 字段名与数据库列名不一致（Java 字段为 fans，数据库列为 fans_count）</p>
     */
    @TableField("fans_count")
    @JsonProperty("fansCount")
    private Long fans;

    /**
     * 粉丝等级
     * <p>对应数据库列：{@code fans_level}，达人的粉丝量级标签，
     * 如 "百万粉丝"、"千万粉丝" 等</p>
     */
    @TableField("fans_level")
    private String level;

    /**
     * 头像链接
     * <p>对应数据库列：{@code avatar_url}，达人抖音头像的 CDN URL</p>
     */
    @TableField("avatar_url")
    private String avatarUrl;

    /**
     * 简介
     * <p>对应数据库列：{@code intro}，达人的抖音个人简介文本</p>
     */
    @TableField("intro")
    private String intro;

    /**
     * 内容分类
     * <p>对应数据库列：{@code categories}，达人的内容领域分类，
     * 如 "美妆"、"服饰"、"食品" 等，多个分类用逗号分隔</p>
     */
    @TableField("categories")
    private String categories;

    /**
     * 联系电话
     * <p>对应数据库列：{@code contact_phone}，达人联系电话（业务手动维护）</p>
     */
    @TableField("contact_phone")
    private String contactPhone;

    /**
     * 联系微信
     * <p>对应数据库列：{@code contact_wechat}，达人微信号（业务手动维护）</p>
     */
    @TableField("contact_wechat")
    private String contactWechat;

    /**
     * 达人标签
     * <p>JSONB 数组格式，对应数据库列：{@code talent_tags}，
     * 系统和手动为达人打的标签列表，如 "优质达人"、"新达人" 等</p>
     */
    @TableField(value = "talent_tags", typeHandler = JsonbListTypeHandler.class)
    private List<String> tags;

    /**
     * 标签更新人
     * <p>对应数据库列：{@code tag_updated_by}，最后一次修改达人标签的操作人 ID</p>
     */
    @TableField("tag_updated_by")
    private UUID tagUpdatedBy;

    /**
     * 寄样收件人姓名
     * <p>对应数据库列：{@code shipping_recipient_name}，寄样快递的收件人姓名</p>
     */
    @TableField("shipping_recipient_name")
    private String shippingRecipientName;

    /**
     * 寄样收件人电话
     * <p>对应数据库列：{@code shipping_recipient_phone}，寄样快递的收件人电话</p>
     */
    @TableField("shipping_recipient_phone")
    private String shippingRecipientPhone;

    /**
     * 寄样收件地址
     * <p>对应数据库列：{@code shipping_recipient_address}，寄样快递的收件详细地址</p>
     */
    @TableField("shipping_recipient_address")
    private String shippingRecipientAddress;

    /**
     * 获赞数
     * <p>对应数据库列：{@code likes_count}，达人所有作品获得的总点赞数</p>
     */
    @TableField("likes_count")
    private Long likesCount;

    /**
     * 关注数
     * <p>对应数据库列：{@code following_count}，达人关注的其他用户数</p>
     */
    @TableField("following_count")
    private Long followingCount;

    /**
     * 作品数
     * <p>对应数据库列：{@code works_count}，达人发布的视频/直播作品总数</p>
     */
    @TableField("works_count")
    private Long worksCount;

    /**
     * IP 归属地
     * <p>对应数据库列：{@code ip_location}，达人最近活跃的 IP 归属地（省级）</p>
     */
    @TableField("ip_location")
    private String ipLocation;

    /**
     * 爬虫抓取状态
     * <p>对应数据库列：{@code crawl_status}，
     * 0=未抓取, 1=抓取中, 2=成功, 3=失败</p>
     */
    @TableField("crawl_status")
    private Integer crawlStatus;

    /**
     * 爬虫抓取信息
     * <p>对应数据库列：{@code crawl_message}，爬虫抓取失败时的错误信息</p>
     */
    @TableField("crawl_message")
    private String crawlMessage;

    /**
     * 最后抓取时间
     * <p>对应数据库列：{@code last_crawl_at}，最近一次爬虫抓取的执行时间</p>
     */
    @TableField("last_crawl_at")
    private LocalDateTime lastCrawlAt;

    /**
     * 资料补全状态
     * <p>对应数据库列：{@code enrich_status}，异步资料补全任务的当前状态，
     * 如 "PENDING"（待处理）、"PROCESSING"（处理中）、"COMPLETED"（完成）、"FAILED"（失败）</p>
     */
    @TableField("enrich_status")
    private String enrichStatus;

    /**
     * 最后补全时间
     * <p>对应数据库列：{@code last_enrich_time}，最近一次资料补全的完成时间</p>
     */
    @TableField("last_enrich_time")
    private LocalDateTime lastEnrichTime;

    /**
     * 数据来源
     * <p>对应数据库列：{@code data_source}，达人资料的数据来源标识，
     * 如 "CRAWLER"（爬虫）、"DOUYIN_API"（抖音官方接口）、"MANUAL"（手动录入）</p>
     */
    @TableField("data_source")
    private String dataSource;

    /**
     * 抖音账号
     * <p>对应数据库列：{@code douyin_account}，达人抖音登录账号信息</p>
     */
    @TableField("douyin_account")
    private String douyinAccount;

    /**
     * 达人 UID（外部）
     * <p>对应数据库列：{@code talent_uid}，外部数据源的达人标识，
     * 可能与 douyinUid 不同（如第三方数据源的编号）</p>
     */
    @TableField("talent_uid")
    private String talentUid;

    /**
     * 达人等级
     * <p>对应数据库列：{@code talent_level}，平台赋予达人的等级标签</p>
     */
    @TableField("talent_level")
    private String talentLevel;

    /**
     * 近 30 天销售额（单位：分）
     * <p>对应数据库列：{@code sales_30d}，达人近 30 天的带货销售总额</p>
     */
    @TableField("sales_30d")
    private Long sales30d;

    /**
     * 同步状态
     * <p>对应数据库列：{@code sync_status}，达人资料同步的当前状态，
     * 如 "PENDING"（待同步）、"SUCCESS"（成功）、"PARTIAL"（部分成功）、"FAILED"（失败）</p>
     */
    @TableField("sync_status")
    private String syncStatus;

    /**
     * 最后同步时间
     * <p>对应数据库列：{@code last_sync_time}，最近一次达人资料同步的时间</p>
     */
    @TableField("last_sync_time")
    private LocalDateTime lastSyncTime;

    /**
     * 同步错误码
     * <p>对应数据库列：{@code sync_error_code}，同步失败时的错误编码</p>
     */
    @TableField("sync_error_code")
    private String syncErrorCode;

    /**
     * 同步错误信息
     * <p>对应数据库列：{@code sync_error_message}，同步失败时的错误描述</p>
     */
    @TableField("sync_error_message")
    private String syncErrorMessage;

    /**
     * 原始响应载荷
     * <p>JSON 对象格式，对应数据库列：{@code raw_payload}，
     * 存储数据源返回的完整原始数据，用于问题排查和数据回放</p>
     */
    @TableField(value = "raw_payload", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> rawPayload;

    /**
     * 不支持的字段列表
     * <p>JSON 数组格式，对应数据库列：{@code unsupported_fields}，
     * 记录数据源不支持或返回异常的字段名称列表</p>
     */
    @TableField(value = "unsupported_fields", typeHandler = JsonbListTypeHandler.class)
    private List<String> unsupportedFields;

    /**
     * 是否黑名单
     * <p>对应数据库列：{@code blacklisted}，标记达人是否被列入黑名单。
     * 黑名单达人不再出现在达人工池中</p>
     */
    @TableField("blacklisted")
    private Boolean blacklisted;

    /**
     * 黑名单原因
     * <p>对应数据库列：{@code blacklist_reason}，将达人加入黑名单的原因说明</p>
     */
    @TableField("blacklist_reason")
    private String blacklistReason;

    /**
     * 状态
     * <p>1=正常, 0=禁用。控制达人是否可被业务操作</p>
     */
    private Integer status;

    /**
     * 月度销售额（非持久化）
     * <p>非数据库持久化字段（exist = false），通过关联查询填充，
     * 用于达人列表展示月度带货金额</p>
     */
    @TableField(exist = false)
    private Long monthlySales;

    /**
     * 负责人用户 ID（非持久化）
     * <p>非数据库持久化字段（exist = false），从 talent_claim 表关联查询，
     * 标识当前认领该达人的业务人员</p>
     */
    @TableField(exist = false)
    private UUID ownerId;

    /**
     * 认领时间（非持久化）
     * <p>非数据库持久化字段（exist = false），从 talent_claim 表关联查询，
     * 达人最近一次被认领的时间</p>
     */
    @TableField(exist = false)
    private LocalDateTime claimedAt;

    /**
     * 达人池状态（非持久化）
     * <p>非数据库持久化字段（exist = false），标识达人当前所在池的状态，
     * 如 "AVAILABLE"（可认领）、"CLAIMED"（已认领）、"PROTECTED"（保护期中）</p>
     */
    @TableField(exist = false)
    private String poolStatus;

    /**
     * 负责人姓名（非持久化）
     * <p>非数据库持久化字段（exist = false），从用户表关联查询，用于前端展示</p>
     */
    @TableField(exist = false)
    private String ownerName;

    /**
     * 保护期截止时间（非持久化）
     * <p>非数据库持久化字段（exist = false），从 talent_claim 表关联查询，
     * 保护期内其他人员无法认领该达人</p>
     */
    @TableField(exist = false)
    private LocalDateTime protectedUntil;

    /**
     * 有效认领数量（非持久化）
     * <p>非数据库持久化字段（exist = false），统计该达人当前有效认领记录数</p>
     */
    @TableField(exist = false)
    private Integer activeClaimCount;

    /**
     * 寄样次数（非持久化）
     * <p>非数据库持久化字段（exist = false），统计该达人历史寄样请求总数</p>
     */
    @TableField(exist = false)
    private Long sampleCount;

    /**
     * 订单数（非持久化）
     * <p>非数据库持久化字段（exist = false），统计该达人关联的历史订单总数</p>
     */
    @TableField(exist = false)
    private Long orderCount;

    /**
     * 服务费贡献（非持久化）
     * <p>非数据库持久化字段（exist = false），统计该达人带来的历史服务费总额</p>
     */
    @TableField(exist = false)
    private Long serviceFeeContribution;

    /**
     * 是否自然单达人（非持久化）
     * <p>非数据库持久化字段（exist = false），标记达人是否通过自然订单（非推广链接）产生业绩</p>
     */
    @TableField(exist = false)
    private Boolean naturalOrderTalent;

    /**
     * 主营类目（非持久化）
     * <p>非数据库持久化字段（exist = false），达人带货的主要商品类目</p>
     */
    @TableField(exist = false)
    private String mainCategory;

    /**
     * 直播销售额区间（非持久化）
     * <p>非数据库持久化字段（exist = false），达人直播带货的销售额分层标签</p>
     */
    @TableField(exist = false)
    private String liveSalesBand;

    /**
     * 直播观看量区间（非持久化）
     * <p>非数据库持久化字段（exist = false），达人直播的场均观看量分层标签</p>
     */
    @TableField(exist = false)
    private String liveViewBand;

    /**
     * 直播 GPM 区间（非持久化）
     * <p>非数据库持久化字段（exist = false），达人直播每千次观看销售额（GPM）分层标签</p>
     */
    @TableField(exist = false)
    private String liveGpmBand;

    /**
     * 短视频销售额区间（非持久化）
     * <p>非数据库持久化字段（exist = false），达人短视频带货的销售额分层标签</p>
     */
    @TableField(exist = false)
    private String videoSalesBand;

    /**
     * 短视频播放量区间（非持久化）
     * <p>非数据库持久化字段（exist = false），达人短视频的平均播放量分层标签</p>
     */
    @TableField(exist = false)
    private String videoPlayBand;

    /**
     * 短视频 GPM 区间（非持久化）
     * <p>非数据库持久化字段（exist = false），达人短视频每千次播放销售额（GPM）分层标签</p>
     */
    @TableField(exist = false)
    private String videoGpmBand;
}
