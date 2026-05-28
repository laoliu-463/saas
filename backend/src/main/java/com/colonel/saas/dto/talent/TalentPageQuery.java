package com.colonel.saas.dto.talent;

import com.colonel.saas.common.enums.DataScope;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.UUID;

/**
 * 达人分页查询请求 DTO。
 * <p>
 * 用于达人列表页的多条件筛选与分页查询，支持按抖音号、昵称、平台、地区、池状态、
 * 归属负责人、粉丝数区间、直播/视频数据区间、达人等级、性别、联系方式等多种维度过滤。
 * 同时携带当前用户的数据范围信息，用于数据权限控制。
 * 关联业务领域：达人域（Talent）。
 * </p>
 */
@Data
public class TalentPageQuery {

    /** 页码，最小值为 1，默认 1 */
    @Min(1)
    private long page = 1;

    /** 每页条数，范围 1-100，默认 10 */
    @Min(1)
    @Max(100)
    private long size = 10;

    /** 关键词搜索（模糊匹配昵称、抖音号等） */
    private String keyword;
    /** 精确抖音号筛选 */
    private String douyinNo;
    /** 精确昵称筛选 */
    private String nickname;
    /** 平台来源筛选 */
    private String platform;
    /** IP 归属地区筛选 */
    private String region;
    /** 达人池状态筛选（如 in_pool、claimed） */
    private String poolStatus;
    /** 归属负责人关键词搜索 */
    private String ownerKeyword;
    /** 粉丝数下限 */
    private Long minFans;
    /** 粉丝数上限 */
    private Long maxFans;

    /** 视图模式（列表视图切换） */
    private String view;
    /** 主推类目筛选 */
    private String category;
    /** 认领状态筛选 */
    private String claimStatus;
    /** 直播销售额区间筛选 */
    private String liveSalesBand;
    /** 直播观看量区间筛选 */
    private String liveViewBand;
    /** 直播 GPM 区间筛选 */
    private String liveGpmBand;
    /** 视频销售额区间筛选 */
    private String videoSalesBand;
    /** 视频播放量区间筛选 */
    private String videoPlayBand;
    /** 视频 GPM 区间筛选 */
    private String videoGpmBand;
    /** 达人等级筛选 */
    private String level;
    /** 性别筛选 */
    private String gender;
    /** 联系状态筛选 */
    private String contactStatus;

    /** 当前操作用户 ID，用于数据范围权限控制 */
    private UUID userId;
    /** 当前操作用户所属部门 ID */
    private UUID deptId;
    /** 数据范围枚举（self/group/all），控制可见数据范围 */
    private DataScope dataScope;
}
