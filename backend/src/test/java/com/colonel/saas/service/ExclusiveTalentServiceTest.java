package com.colonel.saas.service;

import com.colonel.saas.entity.ExclusiveTalent;
import com.colonel.saas.mapper.ExclusiveTalentMapper;
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
class ExclusiveTalentServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private ExclusiveTalentMapper exclusiveTalentMapper;

    private ExclusiveTalentService service;

    @BeforeEach
    void setUp() {
        service = new ExclusiveTalentService(jdbcTemplate, exclusiveTalentMapper);
    }

    @Test
    void findActiveOwnerByTalentUid_shouldReturnOwner() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        ExclusiveTalent record = new ExclusiveTalent();
        record.setUserId(userId);
        record.setDeptId(deptId);
        when(exclusiveTalentMapper.selectOne(any())).thenReturn(record);

        AttributionService.ExclusiveOwner owner = service.findActiveOwnerByTalentUid("talent-1");

        assertThat(owner).isNotNull();
        assertThat(owner.userId()).isEqualTo(userId);
        assertThat(owner.deptId()).isEqualTo(deptId);
    }

    @Test
    void findActiveOwnerByTalentUid_shouldReturnNullWhenNotFound() {
        when(exclusiveTalentMapper.selectOne(any())).thenReturn(null);

        AttributionService.ExclusiveOwner owner = service.findActiveOwnerByTalentUid("talent-x");

        assertThat(owner).isNull();
    }
}
