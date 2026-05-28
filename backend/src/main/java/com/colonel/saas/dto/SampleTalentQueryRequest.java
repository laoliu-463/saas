package com.colonel.saas.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 寄样达人筛选查询请求 DTO。
 * <p>
 * 用于在寄样管理模块中按条件筛选达人列表，支持按关键词、地区、粉丝量范围、信用评分等维度进行组合查询。
 * 关联业务领域：寄样域（Sample）。
 * </p>
 */
@Data
public class SampleTalentQueryRequest {
    /** 搜索关键词，支持达人昵称、抖音号等模糊匹配 */
    private String keyword;
    /** 达人所在地区筛选 */
    private String region;

    /** 最小粉丝数，不能小于 0 */
    @Min(value = 0, message = "minFans 不能小于 0")
    private Long minFans;

    /** 最大粉丝数，不能小于 0 */
    @Min(value = 0, message = "maxFans 不能小于 0")
    private Long maxFans;

    /** 最低信用评分，不能小于 0 */
    @DecimalMin(value = "0.00", message = "minScore 不能小于 0")
    private BigDecimal minScore;

    /** 页码，从 1 开始，默认值 1 */
    @Min(value = 1, message = "page 不能小于 1")
    private Integer page = 1;

    /** 每页条数，默认值 20 */
    @Min(value = 1, message = "size 不能小于 1")
    private Integer size = 20;
}
