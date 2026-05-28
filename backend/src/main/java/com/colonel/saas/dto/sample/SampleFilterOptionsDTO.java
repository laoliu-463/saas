package com.colonel.saas.dto.sample;

import lombok.Data;

import java.util.List;

/**
 * 寄样筛选选项集合 DTO。
 * <p>
 * 返回寄样列表页所有筛选器的选项数据，按维度分组提供下拉选项。
 * 关联业务领域：寄样域（Sample）。
 * </p>
 */
@Data
public class SampleFilterOptionsDTO {
    /** 寄样状态筛选选项列表 */
    private List<SampleFilterOptionItem> statuses;
    /** 合作类型筛选选项列表 */
    private List<SampleFilterOptionItem> cooperationTypes;
    /** 寄样归属类型筛选选项列表 */
    private List<SampleFilterOptionItem> sampleOwnerTypes;
    /** 作业类型筛选选项列表 */
    private List<SampleFilterOptionItem> homeworkTypes;
    /** 渠道筛选选项列表 */
    private List<SampleFilterOptionItem> channels;
    /** 招募人筛选选项列表 */
    private List<SampleFilterOptionItem> recruiters;
    /** 商品筛选选项列表 */
    private List<SampleFilterOptionItem> products;
    /** 合作伙伴筛选选项列表 */
    private List<SampleFilterOptionItem> partners;
    /** 店铺筛选选项列表 */
    private List<SampleFilterOptionItem> shops;
    /** 快递公司筛选选项列表 */
    private List<SampleFilterOptionItem> logisticsCompanies;
}
