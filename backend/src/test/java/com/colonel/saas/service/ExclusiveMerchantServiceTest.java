package com.colonel.saas.service;

import com.colonel.saas.entity.ExclusiveMerchant;
import com.colonel.saas.mapper.ExclusiveMerchantMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExclusiveMerchantServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private ExclusiveMerchantMapper exclusiveMerchantMapper;

    private ExclusiveMerchantService service;

    @BeforeEach
    void setUp() {
        service = new ExclusiveMerchantService(jdbcTemplate, exclusiveMerchantMapper);
    }

    @Test
    void findActiveOwnerByMerchantId_shouldReturnOwner() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        ExclusiveMerchant record = new ExclusiveMerchant();
        record.setUserId(userId);
        record.setDeptId(deptId);
        when(exclusiveMerchantMapper.selectOne(any())).thenReturn(record);

        AttributionService.ExclusiveOwner owner = service.findActiveOwnerByMerchantId("merchant-1");

        assertThat(owner).isNotNull();
        assertThat(owner.userId()).isEqualTo(userId);
        assertThat(owner.deptId()).isEqualTo(deptId);
    }

    @Test
    void findActiveOwnerByMerchantId_shouldReturnNullWhenNotFound() {
        when(exclusiveMerchantMapper.selectOne(any())).thenReturn(null);

        AttributionService.ExclusiveOwner owner = service.findActiveOwnerByMerchantId("merchant-x");

        assertThat(owner).isNull();
    }
}
