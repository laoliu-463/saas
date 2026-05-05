package com.colonel.saas.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 覆盖所有 VO 类的 getter/setter。
 */
class VoTest {

    @Nested
    @DisplayName("SysRoleVO")
    class SysRoleVOTest {

        @Test
        void getterSetter() {
            SysRoleVO vo = new SysRoleVO();
            UUID id = UUID.randomUUID();
            vo.setId(id);
            vo.setRoleCode("admin");
            vo.setRoleName("管理员");
            vo.setDataScope(3);
            vo.setStatus(1);
            vo.setRemark("系统管理员");

            assertThat(vo.getId()).isEqualTo(id);
            assertThat(vo.getRoleCode()).isEqualTo("admin");
            assertThat(vo.getRoleName()).isEqualTo("管理员");
            assertThat(vo.getDataScope()).isEqualTo(3);
            assertThat(vo.getStatus()).isEqualTo(1);
            assertThat(vo.getRemark()).isEqualTo("系统管理员");
        }

        @Test
        void defaultNull() {
            SysRoleVO vo = new SysRoleVO();
            assertThat(vo.getId()).isNull();
            assertThat(vo.getRoleCode()).isNull();
        }
    }

    @Nested
    @DisplayName("SysUserVO")
    class SysUserVOTest {

        @Test
        void getterSetter() {
            SysUserVO vo = new SysUserVO();
            UUID id = UUID.randomUUID();
            UUID deptId = UUID.randomUUID();
            LocalDateTime now = LocalDateTime.now();

            vo.setId(id);
            vo.setUsername("admin");
            vo.setRealName("管理员");
            vo.setPhone("13800138000");
            vo.setEmail("admin@test.com");
            vo.setDeptId(deptId);
            vo.setStatus(1);
            vo.setLastLoginAt(now);
            vo.setCreateTime(now);
            vo.setRoleIds(List.of(UUID.randomUUID()));

            assertThat(vo.getId()).isEqualTo(id);
            assertThat(vo.getUsername()).isEqualTo("admin");
            assertThat(vo.getRealName()).isEqualTo("管理员");
            assertThat(vo.getPhone()).isEqualTo("13800138000");
            assertThat(vo.getEmail()).isEqualTo("admin@test.com");
            assertThat(vo.getDeptId()).isEqualTo(deptId);
            assertThat(vo.getStatus()).isEqualTo(1);
            assertThat(vo.getLastLoginAt()).isEqualTo(now);
            assertThat(vo.getCreateTime()).isEqualTo(now);
            assertThat(vo.getRoleIds()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("SampleTalentVO")
    class SampleTalentVOTest {

        @Test
        void getterSetter() {
            SampleTalentVO vo = new SampleTalentVO();
            vo.setTalentId("T001");
            vo.setNickname("达人小王");
            vo.setAvatarUrl("http://avatar.jpg");
            vo.setFansCount(100000L);
            vo.setCreditScore(new BigDecimal("4.5"));
            vo.setMainCategory("美妆");
            vo.setRegion("北京");

            assertThat(vo.getTalentId()).isEqualTo("T001");
            assertThat(vo.getNickname()).isEqualTo("达人小王");
            assertThat(vo.getAvatarUrl()).isEqualTo("http://avatar.jpg");
            assertThat(vo.getFansCount()).isEqualTo(100000L);
            assertThat(vo.getCreditScore()).isEqualByComparingTo(new BigDecimal("4.5"));
            assertThat(vo.getMainCategory()).isEqualTo("美妆");
            assertThat(vo.getRegion()).isEqualTo("北京");
        }

        @Test
        void defaultNull() {
            SampleTalentVO vo = new SampleTalentVO();
            assertThat(vo.getTalentId()).isNull();
            assertThat(vo.getFansCount()).isNull();
            assertThat(vo.getCreditScore()).isNull();
        }
    }
}
