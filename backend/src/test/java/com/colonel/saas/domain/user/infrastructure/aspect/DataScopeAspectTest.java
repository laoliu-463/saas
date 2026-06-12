package com.colonel.saas.domain.user.infrastructure.aspect;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.domain.user.api.CurrentUserProvider;
import com.colonel.saas.domain.user.api.DataScope;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.colonel.saas.dto.user.UserDataScopeResponse;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataScopeAspectTest {

    @Mock
    private UserDomainFacade userDomainFacade;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Test
    void selfScopeShouldAppendUserIdsCondition() throws Throwable {
        UUID selfId = UUID.randomUUID();
        when(currentUserProvider.currentUserId()).thenReturn(selfId);
        when(userDomainFacade.resolveDataScope(selfId))
                .thenReturn(new UserDataScopeResponse("self", 1, List.of(selfId)));

        QueryWrapper<Object> wrapper = new QueryWrapper<>();
        ProceedingJoinPoint joinPoint = mockJoinPoint(wrapper);

        new DataScopeAspect(userDomainFacade, currentUserProvider).around(joinPoint, annotation());

        assertThat(wrapper.getSqlSegment()).contains("sr.channel_user_id", "IN");
        assertThat(wrapper.getParamNameValuePairs()).containsValue(selfId);
        verify(joinPoint, times(1)).proceed();
    }

    @Test
    void groupScopeShouldAppendAllVisibleUserIds() throws Throwable {
        UUID selfId = UUID.randomUUID();
        UUID peerId = UUID.randomUUID();
        UUID anotherPeerId = UUID.randomUUID();
        List<UUID> userIds = List.of(selfId, peerId, anotherPeerId);
        when(currentUserProvider.currentUserId()).thenReturn(selfId);
        when(userDomainFacade.resolveDataScope(selfId))
                .thenReturn(new UserDataScopeResponse("group", 2, userIds));

        QueryWrapper<Object> wrapper = new QueryWrapper<>();
        ProceedingJoinPoint joinPoint = mockJoinPoint(wrapper);

        new DataScopeAspect(userDomainFacade, currentUserProvider).around(joinPoint, annotation());

        assertThat(wrapper.getSqlSegment()).contains("sr.channel_user_id", "IN");
        assertThat(wrapper.getParamNameValuePairs().values()).containsExactlyInAnyOrderElementsOf(userIds);
        verify(joinPoint, times(1)).proceed();
    }

    @Test
    void allScopeWithEmptyUserIdsShouldNotAppendCondition() throws Throwable {
        UUID userId = UUID.randomUUID();
        when(currentUserProvider.currentUserId()).thenReturn(userId);
        when(userDomainFacade.resolveDataScope(userId))
                .thenReturn(new UserDataScopeResponse("all", 3, List.of()));

        QueryWrapper<Object> wrapper = new QueryWrapper<>();
        ProceedingJoinPoint joinPoint = mockJoinPoint(wrapper);

        new DataScopeAspect(userDomainFacade, currentUserProvider).around(joinPoint, annotation());

        assertThat(wrapper.getSqlSegment()).isEmpty();
        verify(joinPoint, times(1)).proceed();
    }

    @Test
    void nullCurrentUserShouldProceedWithoutResolvingDataScope() throws Throwable {
        when(currentUserProvider.currentUserId()).thenReturn(null);

        QueryWrapper<Object> wrapper = new QueryWrapper<>();
        ProceedingJoinPoint joinPoint = mockJoinPoint(wrapper);

        new DataScopeAspect(userDomainFacade, currentUserProvider).around(joinPoint, annotation());

        assertThat(wrapper.getSqlSegment()).isEmpty();
        verifyNoInteractions(userDomainFacade);
        verify(joinPoint, times(1)).proceed();
    }

    @Test
    void colonelsettlementOrderMapperShouldNotUseDataScopeAnnotation() throws Exception {
        Method method = ColonelsettlementOrderMapper.class.getMethod("findPageWithScope", Page.class, QueryWrapper.class);

        assertThat(method.getAnnotation(DataScope.class)).isNull();
    }

    private ProceedingJoinPoint mockJoinPoint(QueryWrapper<Object> wrapper) throws Throwable {
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        when(point.getArgs()).thenReturn(new Object[]{wrapper});
        when(point.proceed()).thenReturn(null);
        return point;
    }

    private DataScope annotation() throws NoSuchMethodException {
        Method method = DummyMapper.class.getMethod("query", QueryWrapper.class);
        return method.getAnnotation(DataScope.class);
    }

    interface DummyMapper {
        @DataScope(userField = "sr.channel_user_id")
        void query(QueryWrapper<Object> wrapper);
    }
}
