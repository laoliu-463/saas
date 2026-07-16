package com.colonel.saas.domain.user.infrastructure;

import com.colonel.saas.mapper.SysUserMapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysUserRoleRecipientLookupAdapterTest {

    @Mock
    private SysUserMapper sysUserMapper;

    private SysUserRoleRecipientLookupAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SysUserRoleRecipientLookupAdapter(sysUserMapper);
    }

    @Test
    void findActiveUserIdsByRoleCodes_shouldReturnDistinctStableIds() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        List<String> roleCodes = List.of("admin", "biz_leader", "channel_leader");
        when(sysUserMapper.findActiveIdsByRoleCodes(roleCodes))
                .thenReturn(List.of(first, second, first));

        assertThat(adapter.findActiveUserIdsByRoleCodes(roleCodes))
                .containsExactly(first, second);
    }

    @Test
    void findActiveUserIdsByRoleCodes_emptyRolesShouldNotQueryMapper() {
        assertThat(adapter.findActiveUserIdsByRoleCodes(List.of())).isEmpty();

        verify(sysUserMapper, never()).findActiveIdsByRoleCodes(List.of());
    }

    @Test
    void mapperQuery_shouldFilterActiveUsersRolesAndRelations() throws Exception {
        Method method = SysUserMapper.class.getMethod("findActiveIdsByRoleCodes", java.util.Collection.class);
        Select select = method.getAnnotation(Select.class);
        String sql = String.join(" ", select.value()).replaceAll("\\s+", " ").toLowerCase();

        assertThat(sql)
                .contains("select distinct su.id")
                .contains("from sys_user su")
                .contains("join sys_user_role sur")
                .contains("join sys_role sr")
                .contains("su.deleted = 0")
                .contains("sur.deleted = 0")
                .contains("sr.deleted = 0")
                .contains("su.status = 1")
                .contains("<otherwise>")
                .contains("1 = 0");
    }
}
