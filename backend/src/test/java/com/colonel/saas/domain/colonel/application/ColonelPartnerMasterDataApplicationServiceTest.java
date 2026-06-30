package com.colonel.saas.domain.colonel.application;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.entity.ColonelPartner;
import com.colonel.saas.mapper.ColonelPartnerMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ColonelPartnerMasterDataApplicationService 单元测试（DDD-COLONEL-002 Slice 3）。
 *
 * <p>原 ColonelPartnerMasterDataServiceTest 中针对 list / detail / listSources 的断言
 * 已迁移到 Application；Service 委派壳为 1-line delegate，单独测试由集成测试覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
class ColonelPartnerMasterDataApplicationServiceTest {

    @Mock
    private ColonelPartnerMapper colonelPartnerMapper;

    private ColonelPartnerMasterDataApplicationService applicationService;

    @BeforeEach
    void setUp() {
        applicationService = new ColonelPartnerMasterDataApplicationService(colonelPartnerMapper);
    }

    // ========================== 正常返回 ==========================

    @Test
    void list_shouldReturnPagedResultAndAppendAllFilters() {
        ColonelPartner partner = sample("王团长", "BUYIN", "13800000001");
        Page<ColonelPartner> mpPage = new Page<>(1L, 20L);
        mpPage.setTotal(1L);
        mpPage.setRecords(List.of(partner));
        when(colonelPartnerMapper.selectPage(any(Page.class), any(QueryWrapper.class))).thenReturn(mpPage);

        PageResult<ColonelPartner> result = applicationService.list("王", "BUYIN", Boolean.TRUE, 1L, 20L);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getColonelName()).isEqualTo("王团长");

        ArgumentCaptor<QueryWrapper<ColonelPartner>> captor = queryWrapperCaptor();
        verify(colonelPartnerMapper).selectPage(any(Page.class), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertThat(sql).contains("LIKE");
        assertThat(sql).contains("=");
        assertThat(sql).contains("IS NOT NULL");
    }

    @Test
    void list_shouldHandleFalseHasContactWithThreeIsNullConditions() {
        when(colonelPartnerMapper.selectPage(any(Page.class), any(QueryWrapper.class)))
                .thenReturn(emptyPage());

        PageResult<ColonelPartner> result = applicationService.list(null, null, Boolean.FALSE, 1L, 20L);

        assertThat(result.getRecords()).isEmpty();
        ArgumentCaptor<QueryWrapper<ColonelPartner>> captor = queryWrapperCaptor();
        verify(colonelPartnerMapper).selectPage(any(Page.class), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertThat(sql).contains("IS NULL");
        assertThat(sql).doesNotContain("IS NOT NULL");
    }

    @Test
    void list_shouldNotAppendHasContactWhenNull() {
        when(colonelPartnerMapper.selectPage(any(Page.class), any(QueryWrapper.class)))
                .thenReturn(emptyPage());

        applicationService.list("  ", null, null, 1L, 20L);

        ArgumentCaptor<QueryWrapper<ColonelPartner>> captor = queryWrapperCaptor();
        verify(colonelPartnerMapper).selectPage(any(Page.class), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertThat(sql).doesNotContain("LIKE");
        assertThat(sql).doesNotContain("IS NULL");
        assertThat(sql).doesNotContain("IS NOT NULL");
        assertThat(sql).contains("ORDER BY");
    }

    @Test
    void detail_shouldReturnPartnerWhenExists() {
        UUID id = UUID.randomUUID();
        ColonelPartner partner = sample("李团长", "MANUAL", null);
        partner.setId(id);
        when(colonelPartnerMapper.selectById(id)).thenReturn(partner);

        ColonelPartner result = applicationService.detail(id);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(id);
        assertThat(result.getColonelName()).isEqualTo("李团长");
        verify(colonelPartnerMapper).selectById(eq(id));
    }

    @Test
    void listSources_shouldReturnDistinctSortedNonEmptySources() {
        ColonelPartner p1 = sample("甲", "BUYIN", null);
        ColonelPartner p2 = sample("乙", "MANUAL", null);
        ColonelPartner p3 = sample("丙", "BUYIN", null);
        ColonelPartner p4 = sample("丁", null, null);
        when(colonelPartnerMapper.selectList(any(QueryWrapper.class)))
                .thenReturn(List.of(p1, p2, p3, p4));

        List<String> sources = applicationService.listSources();

        assertThat(sources).containsExactly("BUYIN", "MANUAL");
    }

    // ========================== 空数据 ==========================

    @Test
    void list_shouldReturnEmptyRecordsWhenMapperReturnsEmpty() {
        when(colonelPartnerMapper.selectPage(any(Page.class), any(QueryWrapper.class)))
                .thenReturn(emptyPage());

        PageResult<ColonelPartner> result = applicationService.list("不存在的团长", null, null, 1L, 20L);

        assertThat(result.getTotal()).isZero();
        assertThat(result.getRecords()).isEmpty();
    }

    @Test
    void listSources_shouldReturnEmptyListWhenNoPartners() {
        when(colonelPartnerMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());

        List<String> sources = applicationService.listSources();

        assertThat(sources).isEmpty();
    }

    @Test
    void detail_shouldThrowNotFoundWhenMissing() {
        UUID id = UUID.randomUUID();
        when(colonelPartnerMapper.selectById(id)).thenReturn(null);

        assertThatThrownBy(() -> applicationService.detail(id))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("团长主数据不存在");
    }

    // ========================== 鉴权失败 ==========================

    @Test
    void list_shouldNotInjectCallerSpecificFilters_keepingServiceAuthNeutral() {
        when(colonelPartnerMapper.selectPage(any(Page.class), any(QueryWrapper.class)))
                .thenReturn(emptyPage());

        applicationService.list("k", "BUYIN", null, 1L, 20L);

        ArgumentCaptor<QueryWrapper<ColonelPartner>> captor = queryWrapperCaptor();
        verify(colonelPartnerMapper).selectPage(any(Page.class), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertThat(sql).doesNotContain("create_by");
        assertThat(sql).doesNotContain("assignee");
        assertThat(sql).doesNotContain("user_id");
    }

    // ========================== helpers ==========================

    private static ColonelPartner sample(String name, String source, String phone) {
        ColonelPartner p = new ColonelPartner();
        p.setId(UUID.randomUUID());
        p.setColonelName(name);
        p.setSource(source);
        p.setContactPhone(phone);
        return p;
    }

    private static Page<ColonelPartner> emptyPage() {
        Page<ColonelPartner> page = new Page<>(1L, 20L);
        page.setTotal(0L);
        page.setRecords(List.of());
        return page;
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<QueryWrapper<ColonelPartner>> queryWrapperCaptor() {
        return ArgumentCaptor.forClass(QueryWrapper.class);
    }
}