package com.colonel.saas.service;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.talent.TalentDetailResponse;
import com.colonel.saas.dto.talent.TalentPageQuery;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.entity.TalentClaim;
import com.colonel.saas.domain.sample.facade.SampleDomainFacade;
import com.colonel.saas.domain.sample.facade.dto.TalentRecentSampleDTO;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionChecker;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import com.colonel.saas.domain.user.policy.DataScopeResolver;
import com.colonel.saas.domain.user.policy.DataScopePolicy;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.colonel.saas.mapper.TalentClaimMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TalentQueryServiceTest {

    @Mock
    private TalentService talentService;
    @Mock
    private TalentClaimMapper talentClaimMapper;
    @Mock
    private UserDomainFacade userDomainFacade;
    @Mock
    private SampleDomainFacade sampleDomainFacade;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private TalentQueryService talentQueryService;

    @BeforeEach
    void setUp() {
        talentQueryService = new TalentQueryService(
                talentService,
                talentClaimMapper,
                userDomainFacade,
                sampleDomainFacade,
                jdbcTemplate,
                new CurrentUserPermissionChecker(new CurrentUserPermissionPolicy()),
                new DataScopeResolver(new DataScopePolicy()),
                new DddRefactorProperties()
        );
        lenient().when(sampleDomainFacade.countSamplesByTalentIds(any())).thenReturn(Map.of());
        lenient().when(sampleDomainFacade.listRecentSamplesByTalentId(any(), anyInt())).thenReturn(List.of());
    }

    @Test
    void dataScopeAccess_shouldKeepLegacyDefaultAndDelegateEnabledPathToUserPolicy() throws IOException {
        String source = Files.readString(talentQueryServiceSourcePath());

        assertThat(source)
                .contains("dddRefactorProperties.getDataScopePolicy().isEnabled()")
                .contains("assertCanAccessLegacy")
                .contains("assertCanAccessWithPolicy")
                .contains("DataScopeResolver")
                .contains("dataScopeResolver.resolve")
                .contains("CurrentUserPermissionChecker")
                .contains("currentUserPermissionChecker.hasAnyRole")
                .doesNotContain("import com.colonel.saas.domain.user.policy.DataScopePolicy;")
                .doesNotContain("import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;")
                .doesNotContain("dataScopePolicy.")
                .doesNotContain("currentUserPermissionPolicy.");
    }

    private Path talentQueryServiceSourcePath() {
        Path sourcePath = Path.of("src/main/java/com/colonel/saas/service/TalentQueryService.java");
        if (!Files.exists(sourcePath)) {
            sourcePath = Path.of("backend/src/main/java/com/colonel/saas/service/TalentQueryService.java");
        }
        return sourcePath;
    }

    @Test
    void assertCanOperate_shouldRejectChannelStaffWithoutOwnActiveClaim() {
        UUID talentId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        Talent talent = new Talent();
        talent.setId(talentId);
        when(talentService.getById(talentId)).thenReturn(talent);

        TalentClaim otherClaim = new TalentClaim();
        otherClaim.setTalentId(talentId);
        otherClaim.setUserId(otherUserId);
        otherClaim.setStatus(1);
        when(talentClaimMapper.findActiveByTalentId(talentId)).thenReturn(List.of(otherClaim));

        assertThatThrownBy(() -> talentQueryService.assertCanOperate(
                talentId,
                currentUserId,
                null,
                List.of(RoleCodes.CHANNEL_STAFF)
        ))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("无权操作该达人");
    }

    @Test
    void assertCanOperate_shouldAllowChannelLeaderForOwnDeptClaim() {
        UUID talentId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();

        Talent talent = new Talent();
        talent.setId(talentId);
        when(talentService.getById(talentId)).thenReturn(talent);

        TalentClaim deptClaim = new TalentClaim();
        deptClaim.setTalentId(talentId);
        deptClaim.setDeptId(deptId);
        deptClaim.setStatus(1);
        when(talentClaimMapper.findActiveByTalentId(talentId)).thenReturn(List.of(deptClaim));

        talentQueryService.assertCanOperate(
                talentId,
                UUID.randomUUID(),
                deptId,
                List.of(RoleCodes.CHANNEL_LEADER)
        );
    }

    @Test
    void assertCanOperate_shouldNormalizeRoleCodesViaUserPolicy() {
        UUID talentId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();

        Talent talent = new Talent();
        talent.setId(talentId);
        when(talentService.getById(talentId)).thenReturn(talent);

        TalentClaim ownClaim = new TalentClaim();
        ownClaim.setTalentId(talentId);
        ownClaim.setUserId(currentUserId);
        ownClaim.setStatus(1);
        when(talentClaimMapper.findActiveByTalentId(talentId)).thenReturn(List.of(ownClaim));

        talentQueryService.assertCanOperate(
                talentId,
                currentUserId,
                null,
                List.of(" CHANNEL_STAFF ")
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

        when(talentService.getById(talentId)).thenReturn(talent);
        when(talentClaimMapper.selectList(any())).thenReturn(List.of(expiredClaim));
        when(userDomainFacade.loadUserDisplayLabelsByIds(any()))
                .thenReturn(Map.of(ownerId, "渠道负责人-华东组"));
        lenient().when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM sample_request") && sql.contains("talent_id IN")), any(Object[].class)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM colonelsettlement_order") && sql.contains("GROUP BY") && sql.contains("talent_uid") && sql.contains(" IN ")), any(Object[].class)))
                .thenReturn(List.of());
        lenient().when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM sample_request sr")), eq(talentId)))
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
        lenient().when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM sample_request") && sql.contains("talent_id IN")), any(Object[].class)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM colonelsettlement_order") && sql.contains("GROUP BY") && sql.contains("talent_uid") && sql.contains(" IN ")), any(Object[].class)))
                .thenReturn(List.of());

        when(sampleDomainFacade.listRecentSamplesByTalentId(talentId, 20))
                .thenReturn(List.of(
                        new TalentRecentSampleDTO(
                                "MOCK-SAMPLE-REJECT-001",
                                "排查演示商品-推广映射缺失",
                                "REJECTED",
                                "已拒绝",
                                now.minusDays(1),
                                null),
                        new TalentRecentSampleDTO(
                                "MOCK-SAMPLE-CLOSED-001",
                                "排查演示商品-未带推广参数",
                                "CLOSED",
                                "已关闭",
                                now.minusDays(30),
                                null)));
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
    void detail_shouldExposeTagsAndShippingAddressForCollaboration() {
        UUID talentId = UUID.randomUUID();
        UUID viewerId = UUID.randomUUID();

        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDouyinUid("talent_collaboration");
        talent.setNickname("协作达人");
        talent.setTags(List.of("美妆", "高转化"));
        talent.setTagUpdatedBy(viewerId);

        TalentClaim claim = new TalentClaim();
        claim.setTalentId(talentId);
        claim.setUserId(viewerId);
        claim.setStatus(1);
        claim.setRecipientName("张三");
        claim.setRecipientPhone("13800000000");
        claim.setRecipientAddress("上海市浦东新区示例路 1 号");

        when(talentService.getById(talentId)).thenReturn(talent);
        when(talentClaimMapper.selectList(any())).thenReturn(List.of(claim));
        when(talentClaimMapper.findActiveByTalentId(talentId)).thenReturn(List.of(claim));
        lenient().when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM sample_request") && sql.contains("talent_id IN")), any(Object[].class)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM colonelsettlement_order") && sql.contains("GROUP BY") && sql.contains("talent_uid") && sql.contains(" IN ")), any(Object[].class)))
                .thenReturn(List.of());
        lenient().when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM sample_request sr")), eq(talentId)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("COALESCE(extra_data")), eq("talent_collaboration")))
                .thenReturn(List.of());

        TalentDetailResponse response = talentQueryService.detail(talentId, viewerId, null, DataScope.ALL);

        assertThat(response.getTalent().getTags()).containsExactly("美妆", "高转化");
        assertThat(response.getTalent().getTagUpdatedBy()).isEqualTo(viewerId.toString());
        assertThat(response.getClaim().getRecipientName()).isEqualTo("张三");
        assertThat(response.getClaim().getRecipientPhone()).isEqualTo("13800000000");
        assertThat(response.getClaim().getRecipientAddress()).isEqualTo("上海市浦东新区示例路 1 号");
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

        when(talentService.getById(talentId)).thenReturn(talent);
        when(talentClaimMapper.selectList(any())).thenReturn(List.of(claimB, claimA));
        when(talentClaimMapper.findActiveByTalentId(talentId)).thenReturn(List.of(claimB, claimA));
        when(userDomainFacade.loadUserDisplayLabelsByIds(any()))
                .thenReturn(Map.of(ownerA, "渠道A", ownerB, "渠道B"));
        lenient().when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM sample_request") && sql.contains("talent_id IN")), any(Object[].class)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM colonelsettlement_order") && sql.contains("GROUP BY") && sql.contains("talent_uid") && sql.contains(" IN ")), any(Object[].class)))
                .thenReturn(List.of());
        lenient().when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM sample_request sr")), eq(talentId)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("COALESCE(extra_data")), eq("talent_multi_claim")))
                .thenReturn(List.of());

        TalentDetailResponse response = talentQueryService.detail(talentId, viewer, null, DataScope.ALL);

        assertThat(response.getClaim().getPoolStatus()).isEqualTo("PUBLIC");
        assertThat(response.getClaim().getActiveClaimCount()).isEqualTo(2);
        assertThat(response.getClaim().getOwnerName()).contains("等 2 人");
        assertThat(response.getClaim().getActiveClaimOwners()).hasSize(2);
        verify(userDomainFacade, never()).getUsersByIds(any());
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

        TalentPageQuery query = new TalentPageQuery();
        query.setView("TEAM_PUBLIC");
        query.setUserId(myUserId);

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Talent> basePage =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 200, 4);
        basePage.setRecords(List.of(publicTalent, privateTalent, blacklistedTalent, sharedTalent));

        when(talentService.page(1, 10, null, null, null, null, DataScope.ALL, myUserId, null)).thenReturn(basePage);
        when(talentClaimMapper.selectList(any())).thenReturn(List.of(activeClaim, otherActiveClaim));
        when(userDomainFacade.loadUserDisplayLabelsByIds(any()))
                .thenReturn(Map.of(otherUserId, "渠道B"));
        lenient().when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM sample_request") && sql.contains("talent_id IN")), any(Object[].class)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM colonelsettlement_order") && sql.contains("GROUP BY") && sql.contains("talent_uid") && sql.contains(" IN ")), any(Object[].class)))
                .thenReturn(List.of());

        var page = talentQueryService.page(query);

        assertThat(page.getRecords()).hasSize(2);
        assertThat(page.getRecords()).extracting(Talent::getDouyinUid).containsExactlyInAnyOrder("public_1", "shared_1");
        assertThat(page.getRecords()).allMatch(item -> "PUBLIC".equals(item.getPoolStatus()));
    }

    @Test
    void page_shouldRejectUnsupportedGenderFilter() {
        TalentPageQuery query = new TalentPageQuery();
        query.setGender("FEMALE");

        assertThatThrownBy(() -> talentQueryService.page(query))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("gender 筛选当前不支持");
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

        TalentPageQuery query = new TalentPageQuery();
        query.setView("TEAM_PUBLIC");
        query.setUserId(myUserId);
        query.setDataScope(DataScope.PERSONAL);

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Talent> basePage =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 200, 2);
        basePage.setRecords(List.of(publicTalent, sharedTalent));

        when(talentService.page(1, 10, null, null, null, null, DataScope.ALL, myUserId, null)).thenReturn(basePage);
        when(talentClaimMapper.selectList(any())).thenReturn(List.of(otherActiveClaim));
        when(userDomainFacade.loadUserDisplayLabelsByIds(any()))
                .thenReturn(Map.of(otherUserId, "渠道B"));
        lenient().when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM sample_request") && sql.contains("talent_id IN")), any(Object[].class)))
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
        when(userDomainFacade.loadUserDisplayLabelsByIds(any()))
                .thenReturn(Map.of(otherUserId, "渠道同事"));
        lenient().when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM sample_request") && sql.contains("talent_id IN")), any(Object[].class)))
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
        when(userDomainFacade.loadUserDisplayLabelsByIds(any()))
                .thenReturn(Map.of(otherUserId, "渠道同事"));
        lenient().when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM sample_request") && sql.contains("talent_id IN")), any(Object[].class)))
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

        assertThatThrownBy(() -> talentQueryService.detail(talentId, viewer, null, DataScope.PERSONAL))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("无权查看");
    }

    @Test
    void detailAccess_dataScopePolicyEnabledPathShouldPreserveClaimScopeSemantics() {
        DddRefactorProperties properties = new DddRefactorProperties();
        properties.getDataScopePolicy().setEnabled(true);
        TalentQueryService enabledService = new TalentQueryService(
                talentService,
                talentClaimMapper,
                userDomainFacade,
                sampleDomainFacade,
                jdbcTemplate,
                new CurrentUserPermissionChecker(new CurrentUserPermissionPolicy()),
                new DataScopeResolver(new DataScopePolicy()),
                properties
        );
        UUID talentId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();

        Talent talent = new Talent();
        talent.setId(talentId);

        TalentClaim claim = new TalentClaim();
        claim.setTalentId(talentId);
        claim.setUserId(ownerId);
        claim.setDeptId(deptId);
        claim.setStatus(1);
        List<TalentClaim> activeClaims = List.of(claim);

        ReflectionTestUtils.invokeMethod(enabledService, "assertCanAccess",
                talent, ownerId, deptId, DataScope.PERSONAL, activeClaims);
        ReflectionTestUtils.invokeMethod(enabledService, "assertCanAccess",
                talent, ownerId, deptId, DataScope.DEPT, activeClaims);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(enabledService, "assertCanAccess",
                talent, UUID.randomUUID(), deptId, DataScope.PERSONAL, activeClaims))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("无权查看该达人详情");
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(enabledService, "assertCanAccess",
                talent, null, deptId, DataScope.PERSONAL, activeClaims))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("无权查看该达人详情");
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(enabledService, "assertCanAccess",
                talent, ownerId, UUID.randomUUID(), DataScope.DEPT, activeClaims))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("无权查看该达人详情");
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(enabledService, "assertCanAccess",
                talent, ownerId, null, DataScope.DEPT, activeClaims))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("无权查看该达人详情");
    }

    @Test
    void detail_shouldRedactFrontendHiddenFieldsForPersonalScope() {
        UUID talentId = UUID.randomUUID();
        UUID viewer = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDouyinUid("sensitive_uid");
        talent.setSecUid("sec_sensitive");
        talent.setProfileUrl("https://www.douyin.com/user/sec_sensitive");
        talent.setNickname("敏感字段达人");

        TalentClaim claim = new TalentClaim();
        claim.setTalentId(talentId);
        claim.setUserId(viewer);
        claim.setStatus(1);
        claim.setClaimedAt(now.minusDays(2));
        claim.setProtectedUntil(now.plusDays(28));

        Map<String, Object> orderRow = new LinkedHashMap<>();
        orderRow.put("order_id", "ORDER-SENSITIVE-001");
        orderRow.put("product_name", "样例商品");
        orderRow.put("order_amount", 1000L);
        orderRow.put("settle_colonel_commission", 100L);
        orderRow.put("channel_user_name", "不应返回的渠道名");
        orderRow.put("create_time", Timestamp.valueOf(now.minusHours(1)));

        when(talentService.getById(talentId)).thenReturn(talent);
        when(talentClaimMapper.selectList(any())).thenReturn(List.of(claim));
        lenient().when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM sample_request") && sql.contains("talent_id IN")), any(Object[].class)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM colonelsettlement_order") && sql.contains("GROUP BY") && sql.contains("talent_uid") && sql.contains(" IN ")), any(Object[].class)))
                .thenReturn(List.of());
        lenient().when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM sample_request sr")), eq(talentId)))
                .thenReturn(List.of());
        lenient().when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("COALESCE(extra_data")), eq("sensitive_uid")))
                .thenReturn(List.of(orderRow));

        TalentDetailResponse response = talentQueryService.detail(talentId, viewer, null, DataScope.PERSONAL);

        assertThat(response.getTalent().getProfileUrl()).isNull();
        assertThat(response.getClaim().getActiveClaimOwners()).isEmpty();
        assertThat(response.getOrders()).hasSize(1);
        assertThat(response.getOrders().get(0).getChannelName()).isNull();
    }

    @Test
    void detail_shouldNotSerializeSecUidForAnyScope() throws Exception {
        UUID talentId = UUID.randomUUID();

        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDouyinUid("sensitive_uid");
        talent.setSecUid("sec_sensitive");
        talent.setNickname("敏感字段达人");

        when(talentService.getById(talentId)).thenReturn(talent);
        when(talentClaimMapper.selectList(any())).thenReturn(List.of());
        when(talentClaimMapper.findActiveByTalentId(talentId)).thenReturn(List.of());
        lenient().when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM sample_request") && sql.contains("talent_id IN")), any(Object[].class)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM colonelsettlement_order") && sql.contains("GROUP BY") && sql.contains("talent_uid") && sql.contains(" IN ")), any(Object[].class)))
                .thenReturn(List.of());
        lenient().when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM sample_request sr")), eq(talentId)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("COALESCE(extra_data")), eq("sensitive_uid")))
                .thenReturn(List.of());

        TalentDetailResponse response = talentQueryService.detail(talentId, UUID.randomUUID(), null, DataScope.ALL);
        String json = new ObjectMapper().writeValueAsString(response);

        assertThat(json).doesNotContain("secUid");
        assertThat(json).doesNotContain("sec_sensitive");
    }

    @Test
    void page_shouldFilterByNicknameKeywordAndCategoryJson() {
        UUID myUserId = UUID.randomUUID();
        Talent talent = new Talent();
        talent.setId(UUID.randomUUID());
        talent.setDouyinUid("uid_food");
        talent.setDouyinNo("douyin-demo-food");
        talent.setNickname("食品达人");
        talent.setCategories("[\"食品饮料\"]");
        talent.setFans(120000L);
        talent.setPoolStatus("PUBLIC");

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Talent> basePage =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 200, 1);
        basePage.setRecords(List.of(talent));

        TalentPageQuery query = new TalentPageQuery();
        query.setNickname("食品");
        query.setCategory("食品饮料");
        query.setUserId(myUserId);

        when(talentService.page(1, 10, "食品", null, null, null, DataScope.ALL, myUserId, null)).thenReturn(basePage);
        when(talentClaimMapper.selectList(any())).thenReturn(List.of());
        lenient().when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM sample_request") && sql.contains("talent_id IN")), any(Object[].class)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM colonelsettlement_order") && sql.contains("GROUP BY") && sql.contains("talent_uid") && sql.contains(" IN ")), any(Object[].class)))
                .thenReturn(List.of());

        var page = talentQueryService.page(query);

        assertThat(page.getRecords()).hasSize(1);
    }

    @Test
    void page_shouldNotFailWhenCategoryFilterAndTalentHasNoCategoryFields() {
        UUID myUserId = UUID.randomUUID();
        Talent talent = new Talent();
        talent.setId(UUID.randomUUID());
        talent.setDouyinUid("uid_no_category");
        talent.setNickname("无类目达人");
        talent.setPoolStatus("PUBLIC");

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Talent> basePage =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 200, 1);
        basePage.setRecords(List.of(talent));

        TalentPageQuery query = new TalentPageQuery();
        query.setCategory("玩具乐器");
        query.setUserId(myUserId);

        when(talentService.page(1, 10, null, null, null, null, DataScope.ALL, myUserId, null)).thenReturn(basePage);
        when(talentClaimMapper.selectList(any())).thenReturn(List.of());
        lenient().when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM sample_request") && sql.contains("talent_id IN")), any(Object[].class)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM colonelsettlement_order") && sql.contains("GROUP BY") && sql.contains("talent_uid") && sql.contains(" IN ")), any(Object[].class)))
                .thenReturn(List.of());

        var page = talentQueryService.page(query);

        assertThat(page.getRecords()).isEmpty();
        assertThat(page.getTotal()).isZero();
    }

    @Test
    void page_shouldNotFailWhenRegionFilterAndTalentHasNoLocation() {
        UUID myUserId = UUID.randomUUID();
        Talent talent = new Talent();
        talent.setId(UUID.randomUUID());
        talent.setDouyinUid("uid_no_region");
        talent.setNickname("无地域达人");
        talent.setPoolStatus("PRIVATE");
        talent.setOwnerId(myUserId);

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Talent> basePage =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 200, 1);
        basePage.setRecords(List.of(talent));

        TalentPageQuery query = new TalentPageQuery();
        query.setView("MY_TALENTS");
        query.setRegion("广东");
        query.setUserId(myUserId);

        when(talentService.page(1, 10, null, "广东", null, null, DataScope.ALL, myUserId, null)).thenReturn(basePage);
        when(talentClaimMapper.selectList(any())).thenReturn(List.of());
        lenient().when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM sample_request") && sql.contains("talent_id IN")), any(Object[].class)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM colonelsettlement_order") && sql.contains("GROUP BY") && sql.contains("talent_uid") && sql.contains(" IN ")), any(Object[].class)))
                .thenReturn(List.of());

        var page = talentQueryService.page(query);

        assertThat(page.getRecords()).isEmpty();
        assertThat(page.getTotal()).isZero();
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
        lenient().when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM sample_request") && sql.contains("talent_id IN")), any(Object[].class)))
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
        lenient().when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM sample_request") && sql.contains("talent_id IN")), any(Object[].class)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM colonelsettlement_order") && sql.contains("GROUP BY") && sql.contains("talent_uid") && sql.contains(" IN ")), any(Object[].class)))
                .thenReturn(List.of(orderRow));
        lenient().when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM sample_request sr")), eq(talentId)))
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
        lenient().when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM sample_request") && sql.contains("talent_id IN")), any(Object[].class)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.contains("FROM colonelsettlement_order") && sql.contains("GROUP BY") && sql.contains("talent_uid") && sql.contains(" IN ")), any(Object[].class)))
                .thenReturn(List.of());

        var page = talentQueryService.page(query);

        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getRecords()).extracting(Talent::getDouyinUid)
                .containsExactlyInAnyOrder("batch_1", "batch_2");
    }
}
