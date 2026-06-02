package com.colonel.saas.service;

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
 * 团长主数据查询服务单元测试。
 *
 * <p>覆盖 3 类场景：
 * <ul>
 *     <li>正常返回：keyword/source/hasContact 筛选生效，排序按 last_sync_at DESC, colonel_name ASC</li>
 *     <li>空数据：mapper 返回空集时 service 仍返回空 PageResult / 空 List，detail 抛 notFound</li>
 *     <li>鉴权失败：service 本身不鉴权，由 Controller 层 {@code @RequireRoles} 控制；
 *         本测试保证 service 不会自动附加"按调用者过滤"逻辑</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ColonelPartnerMasterDataServiceTest {

    @Mock
    private ColonelPartnerMapper colonelPartnerMapper;

    private ColonelPartnerMasterDataService colonelPartnerMasterDataService;

    @BeforeEach
    void setUp() {
        colonelPartnerMasterDataService = new ColonelPartnerMasterDataService(colonelPartnerMapper);
    }

    // ========================== 正常返回 ==========================

    @Test
    void list_shouldReturnPagedResultAndAppendAllFilters() {
        ColonelPartner partner = sample("王团长", "BUYIN", "13800000001");
        Page<ColonelPartner> mpPage = new Page<>(1L, 20L);
        mpPage.setTotal(1L);
        mpPage.setRecords(List.of(partner));
        when(colonelPartnerMapper.selectPage(any(Page.class), any(QueryWrapper.class))).thenReturn(mpPage);

        PageResult<ColonelPartner> result = colonelPartnerMasterDataService.list("王", "BUYIN", Boolean.TRUE, 1L, 20L);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getColonelName()).isEqualTo("王团长");

        // 验证 wrapper 实际拼接了 like / eq / and(isNotNull...or) 三个条件
        ArgumentCaptor<QueryWrapper<ColonelPartner>> captor = QueryWrapperCaptor();
        verify(colonelPartnerMapper).selectPage(any(Page.class), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertThat(sql).contains("LIKE");
        assertThat(sql).contains("="); // eq(source)
        assertThat(sql).contains("IS NOT NULL");
    }

    @Test
    void list_shouldHandleFalseHasContactWithThreeIsNullConditions() {
        when(colonelPartnerMapper.selectPage(any(Page.class), any(QueryWrapper.class)))
                .thenReturn(emptyPage());

        PageResult<ColonelPartner> result = colonelPartnerMasterDataService.list(null, null, Boolean.FALSE, 1L, 20L);

        assertThat(result.getRecords()).isEmpty();
        ArgumentCaptor<QueryWrapper<ColonelPartner>> captor = QueryWrapperCaptor();
        verify(colonelPartnerMapper).selectPage(any(Page.class), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        // FALSE 走的是连续三个 isNull
        assertThat(sql).contains("IS NULL");
        assertThat(sql).doesNotContain("IS NOT NULL");
    }

    @Test
    void list_shouldNotAppendHasContactWhenNull() {
        when(colonelPartnerMapper.selectPage(any(Page.class), any(QueryWrapper.class)))
                .thenReturn(emptyPage());

        colonelPartnerMasterDataService.list("  ", null, null, 1L, 20L);

        ArgumentCaptor<QueryWrapper<ColonelPartner>> captor = QueryWrapperCaptor();
        verify(colonelPartnerMapper).selectPage(any(Page.class), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        // 空白 keyword 跳过 like; null hasContact 跳过 IS NULL / IS NOT NULL
        assertThat(sql).doesNotContain("LIKE");
        assertThat(sql).doesNotContain("IS NULL");
        assertThat(sql).doesNotContain("IS NOT NULL");
        // 排序仍然存在
        assertThat(sql).contains("ORDER BY");
    }

    @Test
    void detail_shouldReturnPartnerWhenExists() {
        UUID id = UUID.randomUUID();
        ColonelPartner partner = sample("李团长", "MANUAL", null);
        partner.setId(id);
        when(colonelPartnerMapper.selectById(id)).thenReturn(partner);

        ColonelPartner result = colonelPartnerMasterDataService.detail(id);

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
        ColonelPartner p4 = sample("丁", null, null); // null source
        when(colonelPartnerMapper.selectList(any(QueryWrapper.class)))
                .thenReturn(List.of(p1, p2, p3, p4));

        List<String> sources = colonelPartnerMasterDataService.listSources();

        assertThat(sources).containsExactly("BUYIN", "MANUAL");
    }

    // ========================== 空数据 ==========================

    @Test
    void list_shouldReturnEmptyRecordsWhenMapperReturnsEmpty() {
        when(colonelPartnerMapper.selectPage(any(Page.class), any(QueryWrapper.class)))
                .thenReturn(emptyPage());

        PageResult<ColonelPartner> result = colonelPartnerMasterDataService.list("不存在的团长", null, null, 1L, 20L);

        assertThat(result.getTotal()).isZero();
        assertThat(result.getRecords()).isEmpty();
    }

    @Test
    void listSources_shouldReturnEmptyListWhenNoPartners() {
        when(colonelPartnerMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());

        List<String> sources = colonelPartnerMasterDataService.listSources();

        assertThat(sources).isEmpty();
    }

    @Test
    void detail_shouldThrowNotFoundWhenMissing() {
        UUID id = UUID.randomUUID();
        when(colonelPartnerMapper.selectById(id)).thenReturn(null);

        assertThatThrownBy(() -> colonelPartnerMasterDataService.detail(id))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("团长主数据不存在");
    }

    // ========================== 鉴权失败 ==========================
    // service 层不进行角色校验，由 controller 的 @RequireRoles 控制
    // 此处验证 service 不会做越权访问（不会自动附加"只看自己负责的团长"过滤）：
    // 即调用时只按入参 keyword/source/hasContact 拼条件，与 caller 角色无关。

    @Test
    void list_shouldNotInjectCallerSpecificFilters_keepingServiceAuthNeutral() {
        when(colonelPartnerMapper.selectPage(any(Page.class), any(QueryWrapper.class)))
                .thenReturn(emptyPage());

        // 模拟不同角色调用：service 行为必须一致，不应隐式附加 caller 角色过滤
        colonelPartnerMasterDataService.list("k", "BUYIN", null, 1L, 20L);

        ArgumentCaptor<QueryWrapper<ColonelPartner>> captor = QueryWrapperCaptor();
        verify(colonelPartnerMapper).selectPage(any(Page.class), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        // service 不应注入与 caller 角色相关的列
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
    private static ArgumentCaptor<QueryWrapper<ColonelPartner>> QueryWrapperCaptor() {
        return ArgumentCaptor.forClass(QueryWrapper.class);
    }
}
