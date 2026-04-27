package com.colonel.saas.service;

import com.colonel.saas.dto.talent.TalentDetailResponse;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.entity.TalentClaim;
import com.colonel.saas.mapper.SampleRequestMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.mapper.TalentClaimMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TalentQueryServiceTest {

    @Mock
    private TalentService talentService;
    @Mock
    private TalentClaimMapper talentClaimMapper;
    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private SampleRequestMapper sampleRequestMapper;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private TalentQueryService talentQueryService;

    @BeforeEach
    void setUp() {
        talentQueryService = new TalentQueryService(
                talentService,
                talentClaimMapper,
                sysUserMapper,
                sampleRequestMapper,
                jdbcTemplate
        );
    }

    @Test
    void detail_shouldShowExpiredReleaseHintForPublicTalent() {
        UUID talentId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDouyinUid("talent_mock_e");
        talent.setNickname("达人E-保护期到期回公海");

        TalentClaim expiredClaim = new TalentClaim();
        expiredClaim.setTalentId(talentId);
        expiredClaim.setUserId(ownerId);
        expiredClaim.setStatus(2);
        expiredClaim.setClaimedAt(LocalDateTime.now().minusDays(45));
        expiredClaim.setProtectedUntil(LocalDateTime.now().minusDays(15));

        SysUser owner = new SysUser();
        owner.setId(ownerId);
        owner.setRealName("渠道负责人-华东组");

        when(talentService.getById(talentId)).thenReturn(talent);
        when(talentClaimMapper.selectList(any())).thenReturn(List.of(expiredClaim));
        when(sysUserMapper.selectBatchIds(any())).thenReturn(List.of(owner));
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM sample_request WHERE deleted = 0"))))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM colonelsettlement_order") && sql.contains("GROUP BY"))))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM sample_request sr")), eq(talentId)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("COALESCE(extra_data")), eq("talent_mock_e")))
                .thenReturn(List.of());

        TalentDetailResponse response = talentQueryService.detail(talentId);

        assertThat(response.getClaim().getPoolStatus()).isEqualTo("PUBLIC");
        assertThat(response.getClaim().getOwnerName()).contains("已过期释放");
        assertThat(response.getClaim().getOwnerName()).contains("渠道负责人-华东组");
    }

    @Test
    void detail_shouldMapRejectedAndClosedSampleStatuses() {
        UUID talentId = UUID.randomUUID();

        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDouyinUid("talent_mock_g");
        talent.setNickname("达人G-寄样已关闭");

        LocalDateTime now = LocalDateTime.now();

        when(talentService.getById(talentId)).thenReturn(talent);
        when(talentClaimMapper.selectList(any())).thenReturn(List.of());
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM sample_request WHERE deleted = 0"))))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM colonelsettlement_order") && sql.contains("GROUP BY"))))
                .thenReturn(List.of());

        Map<String, Object> rejectedRow = new LinkedHashMap<>();
        rejectedRow.put("id", UUID.randomUUID());
        rejectedRow.put("request_no", "MOCK-SAMPLE-REJECT-001");
        rejectedRow.put("status", 7);
        rejectedRow.put("create_time", Timestamp.valueOf(now.minusDays(1)));
        rejectedRow.put("complete_time", null);
        rejectedRow.put("product_name", "排查演示商品-推广映射缺失");

        Map<String, Object> closedRow = new LinkedHashMap<>();
        closedRow.put("id", UUID.randomUUID());
        closedRow.put("request_no", "MOCK-SAMPLE-CLOSED-001");
        closedRow.put("status", 8);
        closedRow.put("create_time", Timestamp.valueOf(now.minusDays(30)));
        closedRow.put("complete_time", null);
        closedRow.put("product_name", "排查演示商品-未带推广参数");

        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM sample_request sr")), eq(talentId)))
                .thenReturn(List.of(rejectedRow, closedRow));
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("COALESCE(extra_data")), eq("talent_mock_g")))
                .thenReturn(List.of());

        TalentDetailResponse response = talentQueryService.detail(talentId);

        assertThat(response.getSamples()).hasSize(2);
        assertThat(response.getSamples().get(0).getStatus()).isEqualTo("REJECTED");
        assertThat(response.getSamples().get(0).getStatusText()).isEqualTo("已拒绝");
        assertThat(response.getSamples().get(1).getStatus()).isEqualTo("CLOSED");
        assertThat(response.getSamples().get(1).getStatusText()).isEqualTo("已关闭");
    }
}
