package com.colonel.saas.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 寄样达人展示视图对象。
 * <p>
 * 用于寄样管理页面中达人信息的展示，包含达人的基本信息和信用评估数据。
 * 由 Controller 层组装后返回给前端，不直接对应数据库实体，而是聚合了
 * {@code talent} 表中的关键展示字段。
 * </p>
 */
@Data
public class SampleTalentVO {
    /** 达人唯一标识 */
    private String talentId;
    /** 达人昵称 */
    private String nickname;
    /** 达人头像 URL */
    private String avatarUrl;
    /** 粉丝数量 */
    private Long fansCount;
    /** 信用评分，用于寄样决策参考 */
    private BigDecimal creditScore;
    /** 达人主营类目 */
    private String mainCategory;
    /** 达人所在地区 */
    private String region;
}

