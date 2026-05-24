package com.colonel.saas.dto.sample;

import lombok.Data;

import java.util.List;

@Data
public class SampleFilterOptionsDTO {
    private List<SampleFilterOptionItem> statuses;
    private List<SampleFilterOptionItem> cooperationTypes;
    private List<SampleFilterOptionItem> sampleOwnerTypes;
    private List<SampleFilterOptionItem> homeworkTypes;
    private List<SampleFilterOptionItem> channels;
    private List<SampleFilterOptionItem> recruiters;
    private List<SampleFilterOptionItem> products;
    private List<SampleFilterOptionItem> partners;
    private List<SampleFilterOptionItem> shops;
    private List<SampleFilterOptionItem> logisticsCompanies;
}
