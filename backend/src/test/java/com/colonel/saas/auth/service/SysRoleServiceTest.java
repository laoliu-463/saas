package com.colonel.saas.auth.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.auth.dto.SysRoleCreateRequest;
import com.colonel.saas.auth.dto.SysRoleUpdateRequest;
import com.colonel.saas.domain.user.application.SysRoleApplication;
import com.colonel.saas.vo.SysRoleVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysRoleServiceTest {

    @Mock
    SysRoleApplication sysRoleApplication;

    private SysRoleService service;
    private final UUID roleId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SysRoleService(sysRoleApplication);
    }

    @Test
    void findPageDelegatesToApplication() {
        IPage<SysRoleVO> expected = org.mockito.Mockito.mock(IPage.class);
        when(sysRoleApplication.findPage(1, 10, "kw", 1)).thenReturn(expected);

        assertThat(service.findPage(1, 10, "kw", 1)).isSameAs(expected);
        verify(sysRoleApplication, times(1)).findPage(1, 10, "kw", 1);
    }

    @Test
    void getByIdDelegatesToApplication() {
        SysRoleVO expected = new SysRoleVO();
        when(sysRoleApplication.getById(roleId)).thenReturn(expected);

        assertThat(service.getById(roleId)).isSameAs(expected);
        verify(sysRoleApplication, times(1)).getById(roleId);
    }

    @Test
    void findAllEnabledDelegatesToApplication() {
        List<SysRoleVO> expected = List.of();
        when(sysRoleApplication.findAllEnabled()).thenReturn(expected);

        assertThat(service.findAllEnabled()).isSameAs(expected);
        verify(sysRoleApplication, times(1)).findAllEnabled();
    }

    @Test
    void createDelegatesToApplication() {
        SysRoleCreateRequest request = new SysRoleCreateRequest(null, "r", 1, 1, null);
        SysRoleVO expected = new SysRoleVO();
        when(sysRoleApplication.create(request, roleId)).thenReturn(expected);

        assertThat(service.create(request, roleId)).isSameAs(expected);
        verify(sysRoleApplication, times(1)).create(request, roleId);
    }

    @Test
    void updateDelegatesToApplication() {
        SysRoleUpdateRequest request = new SysRoleUpdateRequest("c", "n", 1, 1, null);
        SysRoleVO expected = new SysRoleVO();
        when(sysRoleApplication.update(roleId, request, roleId)).thenReturn(expected);

        assertThat(service.update(roleId, request, roleId)).isSameAs(expected);
        verify(sysRoleApplication, times(1)).update(roleId, request, roleId);
    }

    @Test
    void deleteDelegatesToApplication() {
        service.delete(roleId, roleId);
        verify(sysRoleApplication, times(1)).delete(roleId, roleId);
    }

    @Test
    void constructorDoesNotCallApplication() {
        verifyNoInteractions(sysRoleApplication);
    }
}
