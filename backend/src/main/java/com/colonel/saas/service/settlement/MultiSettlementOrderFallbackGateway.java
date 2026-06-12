package com.colonel.saas.service.settlement;

import com.colonel.saas.douyin.api.OrderApi;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class MultiSettlementOrderFallbackGateway implements SettlementOrderGateway {

    public static final String API_METHOD = "buyin.colonelMultiSettlementOrders";
    public static final String SOURCE = "SETTLEMENT";

    private final OrderApi orderApi;
    private final ObjectMapper objectMapper;

    public MultiSettlementOrderFallbackGateway(OrderApi orderApi, ObjectMapper objectMapper) {
        this.orderApi = orderApi;
        this.objectMapper = objectMapper;
    }

    @Override
    public SettlementOrderPage fetch(SettlementOrderQuery query) {
        String orderIds = query == null || query.safeOrderIds().isEmpty()
                ? null
                : String.join(",", query.safeOrderIds());
        JsonNode response = objectMapper.valueToTree(orderApi.listColonelMultiSettlementOrders(
                null,
                query == null ? null : query.size(),
                query == null ? null : query.cursor(),
                query == null ? null : query.timeType(),
                query == null ? null : query.startTime(),
                query == null ? null : query.endTime(),
                orderIds));
        JsonNode data = response.path("data");
        JsonNode rows = firstArray(data, "order_list", "orderList", "orders", "list", "data");
        List<JsonNode> orders = new ArrayList<>();
        if (rows != null) {
            rows.forEach(orders::add);
        }
        return new SettlementOrderPage(
                List.copyOf(orders),
                firstText(data, "next_cursor", "nextCursor", "cursor", "next_page", "nextPage"),
                firstBoolean(data, "has_more", "hasMore", "has_next", "hasNext"),
                response,
                API_METHOD,
                SOURCE);
    }

    private JsonNode firstArray(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isArray()) {
                return value;
            }
        }
        return null;
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (!value.isMissingNode() && !value.isNull() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return "0";
    }

    private boolean firstBoolean(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isBoolean()) {
                return value.asBoolean();
            }
            if (value.isNumber()) {
                return value.asInt() != 0;
            }
            if (value.isTextual() && !value.asText().isBlank()) {
                return Boolean.parseBoolean(value.asText());
            }
        }
        return false;
    }
}
