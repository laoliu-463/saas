package com.colonel.saas.domain.order.facade;

import com.colonel.saas.entity.PromotionLink;
import com.colonel.saas.mapper.PromotionLinkMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacyPromotionLinkRecordFacadeTest {

    @Mock
    private PromotionLinkMapper promotionLinkMapper;

    private LegacyPromotionLinkRecordFacade facade;

    @BeforeEach
    void setUp() {
        facade = new LegacyPromotionLinkRecordFacade(promotionLinkMapper);
    }

    @Test
    void findByProductId_shouldDelegateToMapper() {
        PromotionLink link = link("ACT-1", "P1");
        when(promotionLinkMapper.selectList(any())).thenReturn(List.of(link));

        assertThat(facade.findByProductId("P1")).containsExactly(link);
        verify(promotionLinkMapper).selectList(any());
        assertThat(facade.findByProductId(" ")).isEmpty();
    }

    @Test
    void findByActivityAndProductIds_shouldDropBlankProductIds() {
        PromotionLink link = link("ACT-1", "P1");
        when(promotionLinkMapper.selectList(any())).thenReturn(List.of(link));

        assertThat(facade.findByActivityAndProductIds("ACT-1", List.of("P1", " ", "P1")))
                .containsExactly(link);
        verify(promotionLinkMapper).selectList(any());
        assertThat(facade.findByActivityAndProductIds("ACT-1", List.of(" "))).isEmpty();
    }

    @Test
    void findByActivityAndProductId_shouldSkipMapperForBlankInput() {
        assertThat(facade.findByActivityAndProductId(" ", "P1")).isEmpty();
        assertThat(facade.findByActivityAndProductId("ACT-1", " ")).isEmpty();

        verify(promotionLinkMapper, never()).selectList(any());
    }

    @Test
    void save_shouldDelegateToMapperAndSkipNull() {
        PromotionLink link = link("ACT-1", "P1");

        facade.save(link);
        facade.save(null);

        verify(promotionLinkMapper).insert(link);
    }

    private static PromotionLink link(String activityId, String productId) {
        PromotionLink link = new PromotionLink();
        link.setActivityId(activityId);
        link.setProductId(productId);
        return link;
    }
}
