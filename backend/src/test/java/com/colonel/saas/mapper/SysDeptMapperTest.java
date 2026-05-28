package com.colonel.saas.mapper;

import com.colonel.saas.entity.SysDept;
import com.colonel.saas.testsupport.BaseIntegrationTest;
import com.colonel.saas.testsupport.DockerAvailable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SysDeptMapper 集成测试 - 使用 Testcontainers 真实 PostgreSQL
 */
@DockerAvailable
class SysDeptMapperTest extends BaseIntegrationTest {

    @Autowired
    private SysDeptMapper sysDeptMapper;

    @Nested
    @DisplayName("findAllActive")
    class FindAllActiveTest {

        @Test
        void shouldReturnEmptyListWhenNoActiveDepts() {
            List<SysDept> result = sysDeptMapper.findAllActive();
            assertThat(result).isEmpty();
        }

        @Test
        void shouldReturnActiveDepts() {
            SysDept dept = createDept("DEPT001", "测试部门");
            sysDeptMapper.insert(dept);

            List<SysDept> result = sysDeptMapper.findAllActive();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getDeptCode()).isEqualTo("DEPT001");
            assertThat(result.get(0).getDeptName()).isEqualTo("测试部门");
        }

        @Test
        void shouldExcludeDeletedDepts() {
            SysDept active = createDept("DEPT001", "活跃部门");
            sysDeptMapper.insert(active);

            SysDept deleted = createDept("DEPT002", "已删除部门");
            sysDeptMapper.insert(deleted);
            sysDeptMapper.softDeleteById(deleted.getId());

            List<SysDept> result = sysDeptMapper.findAllActive();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getDeptCode()).isEqualTo("DEPT001");
        }

        @Test
        void shouldReturnSortedBySortOrderAndName() {
            SysDept dept1 = createDept("DEPT_A", "ZZZ部门");
            dept1.setSortOrder(2);
            sysDeptMapper.insert(dept1);

            SysDept dept2 = createDept("DEPT_B", "AAA部门");
            dept2.setSortOrder(1);
            sysDeptMapper.insert(dept2);

            List<SysDept> result = sysDeptMapper.findAllActive();

            assertThat(result).hasSize(2);
            // 按 sort_order ASC, dept_name ASC 排序
            assertThat(result.get(0).getDeptCode()).isEqualTo("DEPT_B");
            assertThat(result.get(1).getDeptCode()).isEqualTo("DEPT_A");
        }
    }

    @Nested
    @DisplayName("findByDeptCode")
    class FindByDeptCodeTest {

        @Test
        void shouldReturnEmptyWhenNotFound() {
            Optional<SysDept> result = sysDeptMapper.findByDeptCode("NONEXISTENT");
            assertThat(result).isEmpty();
        }

        @Test
        void shouldReturnDeptWhenFound() {
            SysDept dept = createDept("DEPT001", "测试部门");
            sysDeptMapper.insert(dept);

            Optional<SysDept> result = sysDeptMapper.findByDeptCode("DEPT001");

            assertThat(result).isPresent();
            assertThat(result.get().getDeptName()).isEqualTo("测试部门");
        }

        @Test
        void shouldExcludeDeletedDepts() {
            SysDept dept = createDept("DEPT001", "测试部门");
            sysDeptMapper.insert(dept);
            sysDeptMapper.softDeleteById(dept.getId());

            Optional<SysDept> result = sysDeptMapper.findByDeptCode("DEPT001");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("softDeleteById")
    class SoftDeleteByIdTest {

        @Test
        void shouldReturn1WhenDeleted() {
            SysDept dept = createDept("DEPT001", "测试部门");
            sysDeptMapper.insert(dept);

            int rows = sysDeptMapper.softDeleteById(dept.getId());

            assertThat(rows).isEqualTo(1);
        }

        @Test
        void shouldReturn0WhenAlreadyDeleted() {
            SysDept dept = createDept("DEPT001", "测试部门");
            sysDeptMapper.insert(dept);
            sysDeptMapper.softDeleteById(dept.getId());

            int rows = sysDeptMapper.softDeleteById(dept.getId());

            assertThat(rows).isEqualTo(0);
        }

        @Test
        void shouldReturn0WhenIdNotFound() {
            int rows = sysDeptMapper.softDeleteById(UUID.randomUUID());
            assertThat(rows).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("countUsersByDeptId")
    class CountUsersByDeptIdTest {

        @Autowired
        private SysUserMapper sysUserMapper;

        @Test
        void shouldReturnZeroWhenNoUsers() {
            SysDept dept = createDept("DEPT001", "测试部门");
            sysDeptMapper.insert(dept);

            long count = sysDeptMapper.countUsersByDeptId(dept.getId());

            assertThat(count).isZero();
        }

        @Test
        void shouldCountActiveUsersOnly() {
            SysDept dept = createDept("DEPT001", "测试部门");
            sysDeptMapper.insert(dept);

            // 创建活跃用户
            var user1 = createUser("user1", dept.getId());
            sysUserMapper.insert(user1);

            // 创建已删除用户
            var user2 = createUser("user2", dept.getId());
            sysUserMapper.insert(user2);
            sysUserMapper.softDeleteById(user2.getId());

            long count = sysDeptMapper.countUsersByDeptId(dept.getId());

            assertThat(count).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("countChildGroups")
    class CountChildGroupsTest {

        @Test
        void shouldReturnZeroWhenNoChildren() {
            UUID parentId = UUID.randomUUID();
            long count = sysDeptMapper.countChildGroups(parentId);
            assertThat(count).isZero();
        }

        @Test
        void shouldCountChildDepts() {
            SysDept parent = createDept("PARENT", "父部门");
            sysDeptMapper.insert(parent);

            SysDept child1 = createDept("CHILD1", "子部门1");
            child1.setParentId(parent.getId());
            sysDeptMapper.insert(child1);

            SysDept child2 = createDept("CHILD2", "子部门2");
            child2.setParentId(parent.getId());
            sysDeptMapper.insert(child2);

            long count = sysDeptMapper.countChildGroups(parent.getId());

            assertThat(count).isEqualTo(2);
        }

        @Test
        void shouldExcludeDeletedChildren() {
            SysDept parent = createDept("PARENT", "父部门");
            sysDeptMapper.insert(parent);

            SysDept child = createDept("CHILD1", "子部门1");
            child.setParentId(parent.getId());
            sysDeptMapper.insert(child);
            sysDeptMapper.softDeleteById(child.getId());

            long count = sysDeptMapper.countChildGroups(parent.getId());

            assertThat(count).isZero();
        }
    }

    private SysDept createDept(String code, String name) {
        SysDept dept = new SysDept();
        dept.setId(UUID.randomUUID());
        dept.setDeptCode(code);
        dept.setDeptName(name);
        dept.setDeptType("department");
        dept.setSortOrder(0);
        dept.setStatus(1);
        return dept;
    }

    private com.colonel.saas.entity.SysUser createUser(String username, UUID deptId) {
        var user = new com.colonel.saas.entity.SysUser();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setPassword("hashed");
        user.setChannelCode(username.replace("user", "ch"));
        user.setDeptId(deptId);
        user.setStatus(1);
        return user;
    }
}
