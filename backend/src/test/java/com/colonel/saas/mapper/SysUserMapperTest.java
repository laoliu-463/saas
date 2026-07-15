package com.colonel.saas.mapper;

import com.colonel.saas.entity.SysUser;
import com.colonel.saas.testsupport.BaseIntegrationTest;
import com.colonel.saas.testsupport.DockerAvailable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SysUserMapper 集成测试 - 使用 Testcontainers 真实 PostgreSQL
 */
@DockerAvailable
class SysUserMapperTest extends BaseIntegrationTest {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Nested
    @DisplayName("findByUsername")
    class FindByUsernameTest {

        @Test
        void shouldReturnEmptyWhenNotFound() {
            Optional<SysUser> result = sysUserMapper.findByUsername("nonexistent");
            assertThat(result).isEmpty();
        }

        @Test
        void shouldReturnUserWhenFound() {
            SysUser user = createUser("testuser", "ch001");
            sysUserMapper.insert(user);

            Optional<SysUser> result = sysUserMapper.findByUsername("testuser");

            assertThat(result).isPresent();
            assertThat(result.get().getChannelCode()).isEqualTo("ch001");
            assertThat(result.get().getRealName()).isEqualTo("测试用户");
        }

        @Test
        void shouldExcludeDeletedUsers() {
            SysUser user = createUser("testuser", "ch001");
            sysUserMapper.insert(user);
            sysUserMapper.softDeleteById(user.getId());

            Optional<SysUser> result = sysUserMapper.findByUsername("testuser");

            assertThat(result).isEmpty();
        }

        @Test
        void shouldReturnDeletedUserWhenIncludingDeleted() {
            SysUser user = createUser("testuser", "ch001");
            sysUserMapper.insert(user);
            sysUserMapper.softDeleteById(user.getId());

            Optional<SysUser> result = sysUserMapper.findByUsernameIncludingDeleted("testuser");

            assertThat(result).isPresent();
            assertThat(result.get().getDeleted()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("existsByChannelCodeIncludingDeleted")
    class ExistsByChannelCodeIncludingDeletedTest {

        @Test
        void shouldReturnFalseWhenNotExists() {
            boolean exists = sysUserMapper.existsByChannelCodeIncludingDeleted("NONEXISTENT");
            assertThat(exists).isFalse();
        }

        @Test
        void shouldReturnTrueWhenExists() {
            SysUser user = createUser("testuser", "ch001");
            sysUserMapper.insert(user);

            boolean exists = sysUserMapper.existsByChannelCodeIncludingDeleted("ch001");

            assertThat(exists).isTrue();
        }

        @Test
        void shouldReturnTrueEvenWhenDeleted() {
            SysUser user = createUser("testuser", "ch001");
            sysUserMapper.insert(user);
            sysUserMapper.softDeleteById(user.getId());

            boolean exists = sysUserMapper.existsByChannelCodeIncludingDeleted("ch001");

            assertThat(exists).isTrue();
        }
    }

    @Nested
    @DisplayName("softDeleteById")
    class SoftDeleteByIdTest {

        @Test
        void shouldReturn1WhenDeleted() {
            SysUser user = createUser("testuser", "ch001");
            sysUserMapper.insert(user);

            int rows = sysUserMapper.softDeleteById(user.getId());

            assertThat(rows).isEqualTo(1);
        }

        @Test
        void shouldReturn0WhenAlreadyDeleted() {
            SysUser user = createUser("testuser", "ch001");
            sysUserMapper.insert(user);
            sysUserMapper.softDeleteById(user.getId());

            int rows = sysUserMapper.softDeleteById(user.getId());

            assertThat(rows).isEqualTo(0);
        }

        @Test
        void shouldReturn0WhenIdNotFound() {
            int rows = sysUserMapper.softDeleteById(UUID.randomUUID());
            assertThat(rows).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("BaseMapper operations")
    class BaseMapperOperationsTest {

        @Test
        void insertAndSelectById() {
            SysUser user = createUser("testuser", "ch001");
            user.setEmail("test@example.com");
            user.setPhone("13800138000");

            sysUserMapper.insert(user);

            SysUser found = sysUserMapper.selectById(user.getId());

            assertThat(found).isNotNull();
            assertThat(found.getUsername()).isEqualTo("testuser");
            assertThat(found.getEmail()).isEqualTo("test@example.com");
            assertThat(found.getPhone()).isEqualTo("13800138000");
        }

        @Test
        void updateById() {
            SysUser user = createUser("testuser", "ch001");
            sysUserMapper.insert(user);

            user.setRealName("更新后的名称");
            user.setEmail("updated@example.com");
            sysUserMapper.updateById(user);

            SysUser found = sysUserMapper.selectById(user.getId());

            assertThat(found.getRealName()).isEqualTo("更新后的名称");
            assertThat(found.getEmail()).isEqualTo("updated@example.com");
        }

        @Test
        void deleteById() {
            SysUser user = createUser("testuser", "ch001");
            sysUserMapper.insert(user);

            int rows = sysUserMapper.deleteById(user.getId());

            assertThat(rows).isEqualTo(1);
        }
    }

    private SysUser createUser(String username, String channelCode) {
        SysUser user = new SysUser();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setPassword("hashed_password");
        user.setChannelCode(channelCode);
        user.setRealName("测试用户");
        user.setStatus(1);
        return user;
    }
}
