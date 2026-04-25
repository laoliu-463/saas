package com.colonel.saas.gateway.douyin.mock;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.entity.PickSourceMapping;
import com.colonel.saas.gateway.douyin.DouyinOrderGateway;
import com.colonel.saas.mapper.PickSourceMappingMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "douyin.mock.enabled", havingValue = "true")
public class MockDouyinOrderGateway implements DouyinOrderGateway {

    private final PickSourceMappingMapper pickSourceMappingMapper;

    public MockDouyinOrderGateway(PickSourceMappingMapper pickSourceMappingMapper) {
        this.pickSourceMappingMapper = pickSourceMappingMapper;
    }

    @Override
    public OrderListResult listSettlement(DouyinOrderQueryRequest request) {
        List<DouyinOrderItem> orders = new ArrayList<>();
        PickSourceMapping latestMapping = pickSourceMappingMapper.selectOne(
                new LambdaQueryWrapper<PickSourceMapping>()
                        .eq(PickSourceMapping::getStatus, 1)
                        .orderByDesc(PickSourceMapping::getUpdateTime)
                        .last("limit 1")
        );

        long baseTime = request.startTime() > 0 ? request.startTime() : Instant.now().getEpochSecond() - 3600;

        if (latestMapping != null) {
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("product_name", "Mock归因订单商品");
            raw.put("colonel_activity_id", latestMapping.getActivityId());
            raw.put("pick_source", latestMapping.getPickSource());
            raw.put("pick_extra", latestMapping.getPickExtra());
            raw.put("merchant_id", "M_001");
            orders.add(new DouyinOrderItem(
                    "MOCK_ORD_ATTR_" + latestMapping.getShortId(),
                    latestMapping.getProductId(),
                    latestMapping.getProductId(),
                    "800001",
                    "Mock商家A",
                    latestMapping.getTalentId(),
                    latestMapping.getTalentName(),
                    latestMapping.getPickSource(),
                    9900L,
                    1200L,
                    1,
                    baseTime + 3600,
                    null,
                    raw
            ));
        }

        Map<String, Object> unknownRaw = new LinkedHashMap<>();
        unknownRaw.put("product_name", "Mock未归因订单商品");
        unknownRaw.put("merchant_id", "M_002");
        unknownRaw.put("pick_source", "UNKNOWN_PICK_SOURCE");
        unknownRaw.put("pick_extra", "UNKNOWN_PICK_SOURCE");
        orders.add(new DouyinOrderItem(
                "MOCK_ORD_UNATTR_1",
                "EXT_P2",
                latestMapping != null ? latestMapping.getProductId() : "1002",
                "800002",
                "Mock商家B",
                "T_002",
                "达人小王",
                "UNKNOWN_PICK_SOURCE",
                5900L,
                800L,
                1,
                baseTime + 7200,
                null,
                unknownRaw
        ));

        Map<String, Object> noPickRaw = new LinkedHashMap<>();
        noPickRaw.put("product_name", "Mock无归因码订单商品");
        noPickRaw.put("merchant_id", "M_003");
        orders.add(new DouyinOrderItem(
                "MOCK_ORD_UNATTR_2",
                "EXT_P3",
                latestMapping != null ? latestMapping.getProductId() : "1003",
                "800003",
                "Mock商家C",
                "T_003",
                "达人小李",
                null,
                12900L,
                2000L,
                1,
                baseTime + 10800,
                null,
                noPickRaw
        ));

        return new OrderListResult(
                orders,
                false,
                "0",
                Map.of("mock", true, "order_count", orders.size())
        );
    }

    @Override
    public OrderListResult listSettlementWindow(String cursor, Integer count) {
        long now = System.currentTimeMillis() / 1000;
        return listSettlement(new DouyinOrderQueryRequest(now - 3600, now, count == null ? 100 : count, cursor));
    }

    @Override
    public Map<String, Object> decryptSensitiveData(List<String> orderIds) {
        return Map.of("err_no", 0, "data", Map.of("list", List.of()));
    }
}
