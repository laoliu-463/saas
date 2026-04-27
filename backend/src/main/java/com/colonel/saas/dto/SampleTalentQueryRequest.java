package com.colonel.saas.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SampleTalentQueryRequest {
    private String keyword;
    private String region;

    @Min(value = 0, message = "minFans 不能小于 0")
    private Long minFans;

    @Min(value = 0, message = "maxFans 不能小于 0")
    private Long maxFans;

    @DecimalMin(value = "0.00", message = "minScore 不能小于 0")
    private BigDecimal minScore;

    @Min(value = 1, message = "page 不能小于 1")
    private Integer page = 1;

    @Min(value = 1, message = "size 不能小于 1")
    private Integer size = 20;
}
