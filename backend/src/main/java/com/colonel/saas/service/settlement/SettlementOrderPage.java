package com.colonel.saas.service.settlement;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record SettlementOrderPage(
        List<JsonNode> orders,
        String nextCursor,
        boolean hasMore,
        JsonNode rawResponse,
        String apiMethod,
        String source) {
}
