package com.colonel.saas.service.settlement;

import com.colonel.saas.douyin.api.OrderApi;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Primary
@Service
public class InstituteOrderColonelSettlementGateway implements SettlementOrderGateway {

    public static final String API_METHOD = "buyin.instituteOrderColonel";
    public static final String SOURCE = "INSTITUTE_SETTLEMENT";

    private final OrderApi orderApi;
    private final ObjectMapper objectMapper;

    public InstituteOrderColonelSettlementGateway(OrderApi orderApi, ObjectMapper objectMapper) {
        this.orderApi = orderApi;
        this.objectMapper = objectMapper;
    }

    @Override
    public SettlementOrderPage fetch(SettlementOrderQuery query) {
        if (query == null) {
            return emptyPage("query_null");
        }
        if (!query.safeOrderIds().isEmpty()
                && (!StringUtils.hasText(query.startTime()) || !StringUtils.hasText(query.endTime()))) {
            log.warn("settlement_source={} sync_source={} order_ids_ignored=true reason=1603_not_confirmed_support_order_ids count={}",
                    API_METHOD, SOURCE, query.safeOrderIds().size());
            return emptyPage("order_ids_without_time_window");
        }
        JsonNode response = orderApi.listInstituteOrderColonelForSettlement(
                query.startTime(),
                query.endTime(),
                query.timeType(),
                query.size(),
                query.cursor(),
                query.safeOrderIds());
        JsonNode page = pageNode(response);
        return new SettlementOrderPage(
                extractOrders(response),
                firstText(page, "next_cursor", "nextCursor", "cursor", "next_page", "nextPage", "0"),
                firstBoolean(page, "has_more", "hasMore", "has_next", "hasNext"),
                response,
                API_METHOD,
                SOURCE);
    }

    private SettlementOrderPage emptyPage(String reason) {
        JsonNode raw = objectMapper.valueToTree(java.util.Map.of(
                "apiMethod", API_METHOD,
                "source", SOURCE,
                "orders", List.of(),
                "warning", reason));
        return new SettlementOrderPage(List.of(), "0", false, raw, API_METHOD, SOURCE);
    }

    private List<JsonNode> extractOrders(JsonNode root) {
        JsonNode data = dataNode(root);
        JsonNode rows = firstArray(data, "order_list", "orderList", "orders", "list", "data");
        if (rows == null && data.path("data").isObject()) {
            rows = firstArray(data.path("data"), "order_list", "orderList", "orders", "list", "data");
        }
        if (rows == null || !rows.isArray()) {
            return List.of();
        }
        List<JsonNode> orders = new ArrayList<>();
        rows.forEach(orders::add);
        return List.copyOf(orders);
    }

    private JsonNode pageNode(JsonNode root) {
        JsonNode data = dataNode(root);
        JsonNode nested = data.path("data");
        return nested.isObject() ? nested : data;
    }

    private JsonNode dataNode(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return objectMapper.createObjectNode();
        }
        JsonNode data = root.path("data");
        return data.isMissingNode() || data.isNull() ? root : data;
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
            if (!value.isMissingNode() && !value.isNull() && StringUtils.hasText(value.asText())) {
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
            if (value.isTextual() && StringUtils.hasText(value.asText())) {
                return Boolean.parseBoolean(value.asText());
            }
        }
        return false;
    }
}
