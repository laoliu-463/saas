package com.colonel.saas.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.service.CommissionService;
import com.colonel.saas.service.OrderDecryptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataControllerTest {

    @Mock
    private ColonelsettlementOrderMapper orderMapper;
    @Mock
    private OrderDecryptService orderDecryptService;
    @Mock
    private CommissionService commissionService;

    private DataController dataController;

    @BeforeEach
    void setUp() {
        dataController = new DataController(orderMapper, orderDecryptService, commissionService);
    }

    @Test
    void getOrderPage_shouldAlwaysIncludeCreateTimeRange() {
        IPage<ColonelsettlementOrder> empty = new Page<>(1, 10);
        when(orderMapper.findPageWithScope(any(Page.class), any(QueryWrapper.class))).thenReturn(empty);

        dataController.getOrderPage(
                1,
                10,
                null,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30),
                UUID.randomUUID(),
                UUID.randomUUID(),
                DataScope.ALL
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<QueryWrapper<ColonelsettlementOrder>> wrapperCaptor =
                (ArgumentCaptor<QueryWrapper<ColonelsettlementOrder>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(QueryWrapper.class);
        verify(orderMapper).findPageWithScope(any(Page.class), wrapperCaptor.capture());
        String segment = wrapperCaptor.getValue().getSqlSegment();
        assertThat(segment).contains("co.create_time");
    }

    @Test
    void decryptOrderPhones_shouldDelegateToService() {
        DataController.DecryptOrderRequest request = new DataController.DecryptOrderRequest();
        request.setOrderIds(List.of("oid-1"));
        when(orderDecryptService.decryptPhones(List.of("oid-1"))).thenReturn(List.of());

        var response = dataController.decryptOrderPhones(request);

        assertThat(response.getCode()).isEqualTo(200);
        verify(orderDecryptService).decryptPhones(List.of("oid-1"));
    }
}
