package com.colonel.saas.vo;

import com.colonel.saas.entity.Talent;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 达人综合展示视图对象。
 * <p>
 * 这是达人管理模块中最核心的 VO，聚合了达人的全量信息，包括：
 * <ul>
 *   <li>基本信息：抖音号、昵称、头像、等级、类目</li>
 *   <li>社交数据：粉丝数、点赞数、关注数、作品数</li>
 *   <li>带货数据：月销量、直播/视频各维度分段数据</li>
 *   <li>归属信息：所属招商、认领时间、保护期</li>
 *   <li>业务指标：寄样数、订单数、服务费贡献</li>
 *   <li>风控信息：是否拉黑、黑名单原因</li>
 *   <li>物流信息：收货人姓名、电话、地址</li>
 * </ul>
 * </p>
 * <p>
 * 该 VO 在达人列表、达人详情、达人搜索等多个页面复用。
 * </p>
 *
 * @see com.colonel.saas.entity.Talent
 * @see com.colonel.saas.mapper.TalentMapper
 */
@Data
public class TalentVO {
    /** 达人记录 ID */
    private String id;
    /** 抖音平台分配的达人 UID */
    private String douyinUid;
    /** 达人抖音号（用户自定义的短号） */
    private String douyinNo;
    /** 达人系统内部 UID */
    private String uid;
    /** 达人昵称 */
    private String nickname;
    /** 粉丝数量 */
    private Long fansCount;
    /** 达人等级 */
    private String level;
    /** 达人头像 URL */
    private String avatarUrl;
    /** 达人类目（多个类目以逗号分隔） */
    private String categories;
    /** 达人简介/备注 */
    private String remark;
    /** 总点赞数 */
    private Long likesCount;
    /** 关注数 */
    private Long followingCount;
    /** 作品数 */
    private Long worksCount;
    /** IP 属地 */
    private String ipLocation;
    /** 是否已被拉入黑名单 */
    private Boolean blacklisted;
    /** 拉黑原因 */
    private String blacklistReason;
    /** 达人状态 */
    private Integer status;
    /** 月销量 */
    private Long monthlySales;
    /** 达人池状态：标识达人在人才池中的位置 */
    private String poolStatus;
    /** 所属招商人员 ID */
    private String ownerId;
    /** 所属招商人员姓名 */
    private String ownerName;
    /** 认领时间 */
    private LocalDateTime claimedAt;
    /** 保护期截止时间，保护期内其他招商不能认领 */
    private LocalDateTime protectedUntil;
    /** 当前生效的认领记录数 */
    private Integer activeClaimCount;
    /** 寄样数 */
    private Long sampleCount;
    /** 关联订单数 */
    private Long orderCount;
    /** 服务费贡献总额，单位：分 */
    private Long serviceFeeContribution;
    /** 是否为自然订单达人（非主动推广产生订单） */
    private Boolean naturalOrderTalent;
    /** 主营类目 */
    private String mainCategory;
    /** 直播 GMV 分段 */
    private String liveSalesBand;
    /** 直播观看量分段 */
    private String liveViewBand;
    /** 直播 GPM（千次观看成交额）分段 */
    private String liveGpmBand;
    /** 视频带货 GMV 分段 */
    private String videoSalesBand;
    /** 视频播放量分段 */
    private String videoPlayBand;
    /** 视频 GPM 分段 */
    private String videoGpmBand;
    /** 达人标签列表 */
    private List<String> tags;
    /** 寄样收货人姓名 */
    private String shippingRecipientName;
    /** 寄样收货人电话 */
    private String shippingRecipientPhone;
    /** 寄样收货人地址 */
    private String shippingRecipientAddress;

    /**
     * 从 {@code Talent} 实体转换为 VO。
     * <p>
     * 该方法处理 null 值的安全转换，UUID 类型字段转为 String，
     * Boolean 类型做 null 安全处理，数组类型字段直接赋值。
     * </p>
     *
     * @param talent 达人实体，可以为 null
     * @return 对应的 VO 对象，输入为 null 时返回 null
     */
    public static TalentVO from(Talent talent) {
        if (talent == null) {
            return null;
        }
        TalentVO vo = new TalentVO();
        vo.setId(talent.getId() == null ? null : talent.getId().toString());
        vo.setDouyinUid(talent.getDouyinUid());
        vo.setDouyinNo(talent.getDouyinNo());
        vo.setUid(talent.getUid());
        vo.setNickname(talent.getNickname());
        vo.setFansCount(talent.getFans());
        vo.setLevel(talent.getLevel());
        vo.setAvatarUrl(talent.getAvatarUrl());
        vo.setCategories(talent.getCategories());
        vo.setRemark(talent.getIntro());
        vo.setLikesCount(talent.getLikesCount());
        vo.setFollowingCount(talent.getFollowingCount());
        vo.setWorksCount(talent.getWorksCount());
        vo.setIpLocation(talent.getIpLocation());
        vo.setBlacklisted(Boolean.TRUE.equals(talent.getBlacklisted()));
        vo.setBlacklistReason(talent.getBlacklistReason());
        vo.setStatus(talent.getStatus());
        vo.setMonthlySales(talent.getMonthlySales());
        vo.setPoolStatus(talent.getPoolStatus());
        vo.setOwnerId(talent.getOwnerId() == null ? null : talent.getOwnerId().toString());
        vo.setOwnerName(talent.getOwnerName());
        vo.setClaimedAt(talent.getClaimedAt());
        vo.setProtectedUntil(talent.getProtectedUntil());
        vo.setActiveClaimCount(talent.getActiveClaimCount());
        vo.setSampleCount(talent.getSampleCount());
        vo.setOrderCount(talent.getOrderCount());
        vo.setServiceFeeContribution(talent.getServiceFeeContribution());
        vo.setNaturalOrderTalent(talent.getNaturalOrderTalent());
        vo.setMainCategory(talent.getMainCategory());
        vo.setLiveSalesBand(talent.getLiveSalesBand());
        vo.setLiveViewBand(talent.getLiveViewBand());
        vo.setLiveGpmBand(talent.getLiveGpmBand());
        vo.setVideoSalesBand(talent.getVideoSalesBand());
        vo.setVideoPlayBand(talent.getVideoPlayBand());
        vo.setVideoGpmBand(talent.getVideoGpmBand());
        vo.setTags(talent.getTags());
        vo.setShippingRecipientName(talent.getShippingRecipientName());
        vo.setShippingRecipientPhone(talent.getShippingRecipientPhone());
        vo.setShippingRecipientAddress(talent.getShippingRecipientAddress());
        return vo;
    }
}
