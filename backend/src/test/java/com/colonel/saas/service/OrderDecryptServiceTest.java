package com.colonel.saas.service;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.gateway.douyin.DouyinOrderGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderDecryptServiceTest {

    @Mock
    private DouyinOrderGateway douyinOrderGateway;

    private OrderDecryptService orderDecryptService;

    @BeforeEach
    void setUp() {
        orderDecryptService = new OrderDecryptService(douyinOrderGateway);
    }

    @Test
    void decryptPhones_shouldThrowWhenOrderIdsEmpty() {
        assertThatThrownBy(() -> orderDecryptService.decryptPhones(List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("orderIds cannot be empty");
    }

    @Test
    void decryptPhones_shouldThrowWhenOrderIdsExceed50() {
        List<String> orderIds = java.util.stream.IntStream.range(0, 51)
                .mapToObj(i -> "order-" + i)
                .toList();
        assertThatThrownBy(() -> orderDecryptService.decryptPhones(orderIds))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot exceed 50");
    }

    @Test
    void decryptPhones_shouldMaskExpiredVirtualPhone() {
        long expiredEpoch = Instant.now().minusSeconds(10).getEpochSecond();
        when(douyinOrderGateway.decryptSensitiveData(List.of("oid-1"))).thenReturn(Map.of(
                "data", List.of(Map.of(
                        "order_id", "oid-1",
                        "is_virtual_tel", true,
                        "phone_no_a", "138****0001",
                        "phone_no_b", "138****0002",
                        "expire_time", expiredEpoch
                ))
        ));

        List<OrderDecryptService.DecryptPhoneVO> result = orderDecryptService.decryptPhones(List.of("oid-1"));
        assertThat(result).hasSize(1);
        OrderDecryptService.DecryptPhoneVO vo = result.get(0);
        assertThat(vo.isVirtualTel()).isTrue();
        assertThat(vo.isExpired()).isTrue();
        assertThat(vo.getPhoneNoA()).isNull();
        assertThat(vo.getPhoneNoB()).isNull();
    }

    @Test
    void decryptPhones_shouldReturnPlainPhoneForNonVirtual() {
        when(douyinOrderGateway.decryptSensitiveData(List.of("oid-2"))).thenReturn(Map.of(
                "data", List.of(Map.of(
                        "order_id", "oid-2",
                        "is_virtual_tel", false,
                        "phone", "13812345678"
                ))
        ));

        List<OrderDecryptService.DecryptPhoneVO> result = orderDecryptService.decryptPhones(List.of("oid-2"));
        assertThat(result).hasSize(1);
        OrderDecryptService.DecryptPhoneVO vo = result.get(0);
        assertThat(vo.isVirtualTel()).isFalse();
        assertThat(vo.getPhone()).isEqualTo("13812345678");
        assertThat(vo.isExpired()).isFalse();
    }
}
