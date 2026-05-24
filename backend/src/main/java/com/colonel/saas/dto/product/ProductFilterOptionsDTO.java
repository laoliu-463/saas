package com.colonel.saas.dto.product;

import java.util.List;

public record ProductFilterOptionsDTO(
        List<ProductFilterOptionItem> categories) {

    public static ProductFilterOptionsDTO empty() {
        return new ProductFilterOptionsDTO(List.of());
    }
}
