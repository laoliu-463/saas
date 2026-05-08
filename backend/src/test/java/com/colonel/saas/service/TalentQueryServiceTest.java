package com.colonel.saas.service;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.dto.talent.TalentDetailResponse;
import com.colonel.saas.dto.talent.TalentPageQuery;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM sample_request") && sql.contains("talent_id IN")), any(Object[].class)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM colonelsettlement_order") && sql.contains("GROUP BY") && sql.contains("talent_uid") && sql.contains(" IN ")), any(Object[].class)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM sample_request sr")), eq(talentId)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("COALESCE(extra_data")), eq("talent_mock_e")))
                .thenReturn(List.of());

        TalentDetailResponse response = talentQueryService.detail(talentId, ownerId, null, DataScope.ALL);

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
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM sample_request") && sql.contains("talent_id IN")), any(Object[].class)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM colonelsettlement_order") && sql.contains("GROUP BY") && sql.contains("talent_uid") && sql.contains(" IN ")), any(Object[].class)))
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

        TalentDetailResponse response = talentQueryService.detail(talentId, UUID.randomUUID(), null, DataScope.ALL);

        assertThat(response.getSamples()).hasSize(2);
        assertThat(response.getSamples().get(0).getStatus()).isEqualTo("REJECTED");
        assertThat(response.getSamples().get(0).getStatusText()).isEqualTo("已拒绝");
        assertThat(response.getSamples().get(1).getStatus()).isEqualTo("CLOSED");
        assertThat(response.getSamples().get(1).getStatusText()).isEqualTo("已关闭");
    }

    @Test
    void detail_shouldExposeMultiClaimOwnersAndKeepPublicViewForOtherUsers() {
        UUID talentId = UUID.randomUUID();
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        UUID viewer = UUID.randomUUID();

        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDouyinUid("talent_multi_claim");
        talent.setNickname("达人H-多人认领");

        TalentClaim claimA = new TalentClaim();
        claimA.setTalentId(talentId);
        claimA.setUserId(ownerA);
        claimA.setStatus(1);
        claimA.setClaimedAt(LocalDateTime.now().minusDays(3));
        claimA.setProtectedUntil(LocalDateTime.now().plusDays(27));

        TalentClaim claimB = new TalentClaim();
        claimB.setTalentId(talentId);
        claimB.setUserId(ownerB);
        claimB.setStatus(1);
        claimB.setClaimedAt(LocalDateTime.now().minusDays(1));
        claimB.setProtectedUntil(LocalDateTime.now().plusDays(29));

        SysUser userA = new SysUser();
        userA.setId(ownerA);
        userA.setRealName("渠道A");

        SysUser userB = new SysUser();
        userB.setId(ownerB);
        userB.setRealName("渠道B");

        when(talentService.getById(talentId)).thenReturn(talent);
        when(talentClaimMapper.selectList(any())).thenReturn(List.of(claimB, claimA));
        when(talentClaimMapper.findActiveByTalentId(talentId)).thenReturn(List.of(claimB, claimA));
        when(sysUserMapper.selectBatchIds(any())).thenReturn(List.of(userA, userB));
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM sample_request") && sql.contains("talent_id IN")), any(Object[].class)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM colonelsettlement_order") && sql.contains("GROUP BY") && sql.contains("talent_uid") && sql.contains(" IN ")), any(Object[].class)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM sample_request sr")), eq(talentId)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("COALESCE(extra_data")), eq("talent_multi_claim")))
                .thenReturn(List.of());

        TalentDetailResponse response = talentQueryService.detail(talentId, viewer, null, DataScope.ALL);

        assertThat(response.getClaim().getPoolStatus()).isEqualTo("PUBLIC");
        assertThat(response.getClaim().getActiveClaimCount()).isEqualTo(2);
        assertThat(response.getClaim().getOwnerName()).contains("等 2 人");
        assertThat(response.getClaim().getActiveClaimOwners()).hasSize(2);
    }

    @Test
    void page_shouldFilterByView() {
        UUID myUserId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        Talent publicTalent = new Talent();
        publicTalent.setId(UUID.randomUUID());
        publicTalent.setDouyinUid("public_1");
        publicTalent.setPoolStatus("PUBLIC");

        Talent privateTalent = new Talent();
        privateTalent.setId(UUID.randomUUID());
        privateTalent.setDouyinUid("private_1");
        privateTalent.setPoolStatus("PRIVATE");
        privateTalent.setOwnerId(myUserId);

        Talent blacklistedTalent = new Talent();
        blacklistedTalent.setId(UUID.randomUUID());
        blacklistedTalent.setDouyinUid("black_1");
        blacklistedTalent.setBlacklisted(true);

        Talent sharedTalent = new Talent();
        sharedTalent.setId(UUID.randomUUID());
        sharedTalent.setDouyinUid("shared_1");
        sharedTalent.setPoolStatus("PUBLIC");

        TalentClaim activeClaim = new TalentClaim();
        activeClaim.setTalentId(privateTalent.getId());
        activeClaim.setUserId(myUserId);
        activeClaim.setStatus(1);

        TalentClaim otherActiveClaim = new TalentClaim();
        otherActiveClaim.setTalentId(sharedTalent.getId());
        otherActiveClaim.setUserId(otherUserId);
        otherActiveClaim.setStatus(1);

        SysUser otherUser = new SysUser();
        otherUser.setId(otherUserId);
        otherUser.setRealName("渠道B");

        TalentPageQuery query = new TalentPageQuery();
        query.setView("TEAM_PUBLIC");
        query.setUserId(myUserId);

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Talent> basePage =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 200, 4);
        basePage.setRecords(List.of(publicTalent, privateTalent, blacklistedTalent, sharedTalent));

        when(talentService.page(1, 10, null, null, null, null, DataScope.ALL, myUserId, null)).thenReturn(basePage);
        when(talentClaimMapper.selectList(any())).thenReturn(List.of(activeClaim, otherActiveClaim));
        when(sysUserMapper.selectBatchIds(any())).thenReturn(List.of(otherUser));
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM sample_request") && sql.contains("talent_id IN")), any(Object[].class)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM colonelsettlement_order") && sql.contains("GROUP BY") && sql.contains("talent_uid") && sql.contains(" IN ")), any(Object[].class)))
                .thenReturn(List.of());

        var page = talentQueryService.page(query);

        assertThat(page.getRecords()).hasSize(2);
        assertThat(page.getRecords()).extracting(Talent::getDouyinUid).containsExactlyInAnyOrder("public_1", "shared_1");
        assertThat(page.getRecords()).allMatch(item -> "PUBLIC".equals(item.getPoolStatus()));
    }

    @Test
    void page_teamPublicPersonalShouldExcludeTalentsClaimedByOthers() {
        UUID myUserId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        Talent publicTalent = new Talent();
        publicTalent.setId(UUID.randomUUID());
        publicTalent.setDouyinUid("public_only");

        Talent sharedTalent = new Talent();
        sharedTalent.setId(UUID.randomUUID());
        sharedTalent.setDouyinUid("claimed_by_other");

        TalentClaim otherActiveClaim = new TalentClaim();
        otherActiveClaim.setTalentId(sharedTalent.getId());
        otherActiveClaim.setUserId(otherUserId);
        otherActiveClaim.setStatus(1);

        SysUser otherUser = new SysUser();
        otherUser.setId(otherUserId);
        otherUser.setRealName("渠道B");

        TalentPageQuery query = new TalentPageQuery();
        query.setView("TEAM_PUBLIC");
        query.setUserId(myUserId);
        query.setDataScope(DataScope.PERSONAL);

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Talent> basePage =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 200, 2);
        basePage.setRecords(List.of(publicTalent, sharedTalent));

        when(talentService.page(1, 10, null, null, null, null, DataScope.ALL, myUserId, null)).thenReturn(basePage);
        when(talentClaimMapper.selectList(any())).thenReturn(List.of(otherActiveClaim));
        when(sysUserMapper.selectBatchIds(any())).thenReturn(List.of(otherUser));
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM sample_request") && sql.contains("talent_id IN")), any(Object[].class)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM colonelsettlement_order") && sql.contains("GROUP BY") && sql.contains("talent_uid") && sql.contains(" IN ")), any(Object[].class)))
                .thenReturn(List.of());

        var page = talentQueryService.page(query);

        assertThat(page.getRecords()).extracting(Talent::getDouyinUid).containsExactly("public_only");
    }

    @Test
    void page_teamPublicShouldIgnoreDeptScopeAndIncludePublicPool() {
        UUID myUserId = UUID.randomUUID();
        UUID myDeptId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        Talent publicTalent = new Talent();
        publicTalent.setId(UUID.randomUUID());
        publicTalent.setDouyinUid("public_pool_1");

        Talent sharedTalent = new Talent();
        sharedTalent.setId(UUID.randomUUID());
        sharedTalent.setDouyinUid("shared_pool_1");

        TalentClaim otherActiveClaim = new TalentClaim();
        otherActiveClaim.setTalentId(sharedTalent.getId());
        otherActiveClaim.setUserId(otherUserId);
        otherActiveClaim.setStatus(1);

        SysUser otherUser = new SysUser();
        otherUser.setId(otherUserId);
        otherUser.setRealName("渠道同事");

        TalentPageQuery query = new TalentPageQuery();
        query.setView("TEAM_PUBLIC");
        query.setUserId(myUserId);
        query.setDeptId(myDeptId);
        query.setDataScope(DataScope.DEPT);

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Talent> basePage =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 200, 2);
        basePage.setRecords(List.of(publicTalent, sharedTalent));

        when(talentService.page(1, 10, null, null, null, null, DataScope.ALL, myUserId, myDeptId)).thenReturn(basePage);
        when(talentClaimMapper.selectList(any())).thenReturn(List.of(otherActiveClaim));
        when(sysUserMapper.selectBatchIds(any())).thenReturn(List.of(otherUser));
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM sample_request") && sql.contains("talent_id IN")), any(Object[].class)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM colonelsettlement_order") && sql.contains("GROUP BY") && sql.contains("talent_uid") && sql.contains(" IN ")), any(Object[].class)))
                .thenReturn(List.of());

        var page = talentQueryService.page(query);

        assertThat(page.getRecords()).hasSize(2);
        assertThat(page.getRecords()).extracting(Talent::getDouyinUid)
                .containsExactlyInAnyOrder("public_pool_1", "shared_pool_1");
    }

    @Test
    void page_teamPrivateShouldReturnDeptClaimedTalents() {
        UUID myUserId = UUID.randomUUID();
        UUID myDeptId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        Talent myTalent = new Talent();
        myTalent.setId(UUID.randomUUID());
        myTalent.setDouyinUid("my_private_1");
        myTalent.setPoolStatus("PRIVATE");
        myTalent.setOwnerId(myUserId);

        Talent teammateTalent = new Talent();
        teammateTalent.setId(UUID.randomUUID());
        teammateTalent.setDouyinUid("team_private_1");
        teammateTalent.setPoolStatus("PUBLIC");

        Talent publicTalent = new Talent();
        publicTalent.setId(UUID.randomUUID());
        publicTalent.setDouyinUid("public_only_1");
        publicTalent.setPoolStatus("PUBLIC");

        TalentClaim myClaim = new TalentClaim();
        myClaim.setTalentId(myTalent.getId());
        myClaim.setUserId(myUserId);
        myClaim.setDeptId(myDeptId);
        myClaim.setStatus(1);

        TalentClaim teammateClaim = new TalentClaim();
        teammateClaim.setTalentId(teammateTalent.getId());
        teammateClaim.setUserId(otherUserId);
        teammateClaim.setDeptId(myDeptId);
        teammateClaim.setStatus(1);

        SysUser teammate = new SysUser();
        teammate.setId(otherUserId);
        teammate.setRealName("渠道同事");

        TalentPageQuery query = new TalentPageQuery();
        query.setView("TEAM_PRIVATE");
        query.setUserId(myUserId);
        query.setDeptId(myDeptId);
        query.setDataScope(DataScope.DEPT);

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Talent> basePage =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 200, 3);
        basePage.setRecords(List.of(myTalent, teammateTalent, publicTalent));

        when(talentService.page(1, 10, null, null, null, null, DataScope.DEPT, myUserId, myDeptId)).thenReturn(basePage);
        when(talentClaimMapper.selectList(any())).thenReturn(List.of(myClaim, teammateClaim));
        when(sysUserMapper.selectBatchIds(any())).thenReturn(List.of(teammate));
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM sample_request") && sql.contains("talent_id IN")), any(Object[].class)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM colonelsettlement_order") && sql.contains("GROUP BY") && sql.contains("talent_uid") && sql.contains(" IN ")), any(Object[].class)))
                .thenReturn(List.of());

        var page = talentQueryService.page(query);

        assertThat(page.getRecords()).hasSize(2);
        assertThat(page.getRecords()).extracting(Talent::getDouyinUid)
                .containsExactlyInAnyOrder("my_private_1", "team_private_1");
    }

    @Test
    void detail_shouldRejectPersonalScopeWhenViewerHasNoActiveClaim() {
        UUID talentId = UUID.randomUUID();
        UUID viewer = UUID.randomUUID();

        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDouyinUid("talent_private_only");

        when(talentService.getById(talentId)).thenReturn(talent);
        when(talentClaimMapper.findActiveByTalentId(talentId)).thenReturn(List.of());

        assertThatThrownBy(() -> talentQueryService.detail(talentId, viewer, null, DataScope.PERSONAL))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("无权查看");
    }

    @Test
    void page_shouldApplyBusinessFilters() {
        UUID myUserId = UUID.randomUUID();
        Talent talent = new Talent();
        talent.setId(UUID.randomUUID());
        talent.setDouyinUid("food_1");
        talent.setCategories("[\"食品饮料\",\"滋补保健\"]");
        talent.setIpLocation("广东广州");
        talent.setFans(120000L);
        talent.setPoolStatus("PUBLIC");

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Talent> basePage =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 200, 1);
        basePage.setRecords(List.of(talent));

        TalentPageQuery query = new TalentPageQuery();
        query.setCategory("食品饮料");
        query.setRegion("广东");
        query.setClaimStatus("UNCLAIMED");
        query.setMinFans(100000L);
        query.setUserId(myUserId);

        when(talentService.page(1, 10, null, "广东", 100000L, null, DataScope.ALL, myUserId, null)).thenReturn(basePage);
        when(talentClaimMapper.selectList(any())).thenReturn(List.of());
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM sample_request") && sql.contains("talent_id IN")), any(Object[].class)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM colonelsettlement_order") && sql.contains("GROUP BY") && sql.contains("talent_uid") && sql.contains(" IN ")), any(Object[].class)))
                .thenReturn(List.of());

        var page = talentQueryService.page(query);

        assertThat(page.getRecords()).hasSize(1);
    }

    @Test
    void detail_shouldReturnBusinessMetrics() {
        UUID talentId = UUID.randomUUID();

        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDouyinUid("metric_uid");
        talent.setNickname("经营指标达人");
        talent.setCategories("[\"食品饮料\"]");
        talent.setBlacklisted(true);
        talent.setBlacklistReason("重复违约");

        Map<String, Object> orderRow = new LinkedHashMap<>();
        orderRow.put("talent_uid", "metric_uid");
        orderRow.put("order_count", 3L);
        orderRow.put("order_amount", 6800000L);
        orderRow.put("service_fee", 120000L);

        when(talentService.getById(talentId)).thenReturn(talent);
        when(talentClaimMapper.selectList(any())).thenReturn(List.of());
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM sample_request") && sql.contains("talent_id IN")), any(Object[].class)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM colonelsettlement_order") && sql.contains("GROUP BY") && sql.contains("talent_uid") && sql.contains(" IN ")), any(Object[].class)))
                .thenReturn(List.of(orderRow));
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM sample_request sr")), eq(talentId)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("COALESCE(extra_data")), eq("metric_uid")))
                .thenReturn(List.of());

        TalentDetailResponse response = talentQueryService.detail(talentId, UUID.randomUUID(), null, DataScope.ALL);

        assertThat(response.getTalent().getMonthlySales()).isEqualTo(6800000L);
        assertThat(response.getTalent().getMainCategory()).isEqualTo("食品饮料");
        assertThat(response.getTalent().getLiveSalesBand()).isNotNull();
        assertThat(response.getTalent().getBlacklisted()).isTrue();
    }

    @Test
    void page_shouldTraverseAllBatchesInsteadOfStoppingAtOneThousand() {
        UUID myUserId = UUID.randomUUID();

        Talent firstBatchTalent = new Talent();
        firstBatchTalent.setId(UUID.randomUUID());
        firstBatchTalent.setDouyinUid("batch_1");
        firstBatchTalent.setPoolStatus("PUBLIC");

        Talent secondBatchTalent = new Talent();
        secondBatchTalent.setId(UUID.randomUUID());
        secondBatchTalent.setDouyinUid("batch_2");
        secondBatchTalent.setPoolStatus("PUBLIC");

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Talent> firstPage =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 200, 201);
        firstPage.setRecords(List.of(firstBatchTalent));
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Talent> secondPage =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(2, 200, 201);
        secondPage.setRecords(List.of(secondBatchTalent));

        TalentPageQuery query = new TalentPageQuery();
        query.setPage(1);
        query.setSize(10);
        query.setUserId(myUserId);

        when(talentService.page(1, 10, null, null, null, null, DataScope.ALL, myUserId, null)).thenReturn(firstPage);
        when(talentService.page(2, 10, null, null, null, null, DataScope.ALL, myUserId, null)).thenReturn(secondPage);
        when(talentClaimMapper.selectList(any())).thenReturn(List.of());
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM sample_request") && sql.contains("talent_id IN")), any(Object[].class)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM colonelsettlement_order") && sql.contains("GROUP BY") && sql.contains("talent_uid") && sql.contains(" IN ")), any(Object[].class)))
                .thenReturn(List.of());

        var page = talentQueryService.page(query);

        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getRecords()).extracting(Talent::getDouyinUid)
                .containsExactlyInAnyOrder("batch_1", "batch_2");
    }
}
