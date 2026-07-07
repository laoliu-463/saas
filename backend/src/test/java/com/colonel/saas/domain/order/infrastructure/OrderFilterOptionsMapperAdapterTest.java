package com.colonel.saas.domain.order.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.order.application.dto.OrderFilterOptionItem;
import com.colonel.saas.domain.order.application.dto.OrderFilterOptionsQuery;
import com.colonel.saas.domain.user.policy.DataScopePolicy;
import com.colonel.saas.domain.user.policy.DataScopeResolver;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.service.AttributionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderFilterOptionsMapperAdapterTest {

    @Mock
    private ColonelsettlementOrderMapper orderMapper;

    @Test
    void listProductOptions_shouldApplyKeywordAndPersonalScopeThroughMapper() {
        UUID userId = UUID.randomUUID();
        OrderFilterOptionsMapperAdapter adapter = newAdapter();
        when(orderMapper.selectMaps(any())).thenReturn(List.of(Map.of("VALUE", "P-1", "LABEL", "商品一")));

        List<OrderFilterOptionItem> result = adapter.listProductOptions(
                new OrderFilterOptionsQuery("甲", userId, null, DataScope.PERSONAL));

        assertThat(result).containsExactly(new OrderFilterOptionItem("P-1", "商品一"));
        ArgumentCaptor<QueryWrapper> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(orderMapper).selectMaps(captor.capture());
        String sqlSegment = captor.getValue().getSqlSegment();
        assertThat(sqlSegment)
                .contains("product_name")
                .contains("product_id")
                .contains("user_id");
    }

    @Test
    void listUnattributedReasonValues_shouldFilterUnattributedAndApplyDeptScope() {
        UUID deptId = UUID.randomUUID();
        OrderFilterOptionsMapperAdapter adapter = newAdapter();
        when(orderMapper.selectMaps(any())).thenReturn(List.of(Map.of("value", AttributionService.REASON_SYNC_FAILED)));

        List<String> result = adapter.listUnattributedReasonValues(
                new OrderFilterOptionsQuery(null, null, deptId, DataScope.DEPT));

        assertThat(result).containsExactly(AttributionService.REASON_SYNC_FAILED);
        ArgumentCaptor<QueryWrapper> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(orderMapper).selectMaps(captor.capture());
        String sqlSegment = captor.getValue().getSqlSegment();
        assertThat(sqlSegment)
                .contains("attribution_status")
                .contains("attribution_remark")
                .contains("dept_id");
    }

    private OrderFilterOptionsMapperAdapter newAdapter() {
        return new OrderFilterOptionsMapperAdapter(
                orderMapper,
                new DataScopeResolver(new DataScopePolicy()),
                new DddRefactorProperties());
    }
}
