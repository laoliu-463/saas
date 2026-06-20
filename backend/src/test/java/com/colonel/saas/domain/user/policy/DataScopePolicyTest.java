package com.colonel.saas.domain.user.policy;

import com.colonel.saas.common.enums.DataScope;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DataScopePolicy 单元测试（DDD-USER-DATASCOPE-001）。
 *
 * <p>本测试是数据范围收口的<b>硬证据</b>：必须在提取到 Policy 之前/之后都能跑通。
 *
 * <h3>测试目标</h3>
 * <ol>
 *   <li>PERSONAL + 非 null userId → "user_id = ?"</li>
 *   <li>PERSONAL + null userId → ""（防御性，避免越权）</li>
 *   <li>DEPT + 非 null deptId → "dept_id = ?"</li>
 *   <li>DEPT + null deptId → ""</li>
 *   <li>ALL → ""（管理员）</li>
 *   <li>null dataScope → ""</li>
 *   <li>自定义列名支持</li>
 * </ol>
 *
 * <h3>设计优势</h3>
 * <p>Policy 是<b>纯函数</b>（只返回字符串，不修改 wrapper），无需 MyBatis-Plus
 * lambda cache。测试是<b>纯单元测试</b>，无 Spring 上下文，无副作用。</p>
 */
class DataScopePolicyTest {

    private final DataScopePolicy policy = new DataScopePolicy();

    // ===== 5 个核心分支 =====

    @Test
    void personal_withUserId_shouldReturnUserIdEq() {
        UUID userId = UUID.randomUUID();
        String result = policy.buildFilter(userId, null, DataScope.PERSONAL);
        assertThat(result).isEqualTo("user_id = ?");
    }

    @Test
    void personal_withNullUserId_shouldReturnEmpty() {
        String result = policy.buildFilter(null, null, DataScope.PERSONAL);
        // 防御性：userId 为 null 时不追加条件（避免越权返回空集）
        assertThat(result).isEmpty();
    }

    @Test
    void dept_withDeptId_shouldReturnDeptIdEq() {
        UUID deptId = UUID.randomUUID();
        String result = policy.buildFilter(null, deptId, DataScope.DEPT);
        assertThat(result).isEqualTo("dept_id = ?");
    }

    @Test
    void dept_withNullDeptId_shouldReturnEmpty() {
        String result = policy.buildFilter(null, null, DataScope.DEPT);
        assertThat(result).isEmpty();
    }

    @Test
    void all_shouldReturnEmpty() {
        // ALL 不追加任何过滤条件
        String result = policy.buildFilter(UUID.randomUUID(), UUID.randomUUID(), DataScope.ALL);
        assertThat(result).isEmpty();
    }

    // ===== 防御性分支 =====

    @Test
    void nullDataScope_shouldReturnEmpty() {
        String result = policy.buildFilter(UUID.randomUUID(), UUID.randomUUID(), null);
        assertThat(result).isEmpty();
    }

    // ===== 自定义列名 =====

    @Test
    void customColumnName_shouldBeUsed() {
        UUID userId = UUID.randomUUID();
        String result = policy.buildFilter(userId, null, DataScope.PERSONAL,
                "create_by", "dept_id");
        assertThat(result).isEqualTo("create_by = ?");
    }

    @Test
    void customColumnName_dept_shouldBeUsed() {
        UUID deptId = UUID.randomUUID();
        String result = policy.buildFilter(null, deptId, DataScope.DEPT,
                "user_id", "owner_dept_id");
        assertThat(result).isEqualTo("owner_dept_id = ?");
    }

    @Test
    void emptyColumnName_shouldReturnEmpty() {
        // 空列名应当被忽略（防止 SQL 注入风险）
        String result = policy.buildFilter(UUID.randomUUID(), null, DataScope.PERSONAL,
                "", "dept_id");
        assertThat(result).isEmpty();
    }

    @Test
    void nullColumnName_shouldReturnEmpty() {
        // null 列名应当被忽略
        String result = policy.buildFilter(UUID.randomUUID(), null, DataScope.PERSONAL,
                null, "dept_id");
        assertThat(result).isEmpty();
    }

    // ===== decide() 决策方法 =====

    @Test
    void decide_personal_withUserId_shouldReturnFilterUser() {
        UUID userId = UUID.randomUUID();
        DataScopePolicy.Decision decision = policy.decide(userId, null, DataScope.PERSONAL);
        assertThat(decision).isEqualTo(DataScopePolicy.Decision.FILTER_USER);
    }

    @Test
    void decide_personal_withNullUserId_shouldReturnNoFilter() {
        DataScopePolicy.Decision decision = policy.decide(null, null, DataScope.PERSONAL);
        assertThat(decision).isEqualTo(DataScopePolicy.Decision.NO_FILTER);
    }

    @Test
    void decide_dept_withDeptId_shouldReturnFilterDept() {
        UUID deptId = UUID.randomUUID();
        DataScopePolicy.Decision decision = policy.decide(null, deptId, DataScope.DEPT);
        assertThat(decision).isEqualTo(DataScopePolicy.Decision.FILTER_DEPT);
    }

    @Test
    void decide_dept_withNullDeptId_shouldReturnNoFilter() {
        DataScopePolicy.Decision decision = policy.decide(UUID.randomUUID(), null, DataScope.DEPT);
        assertThat(decision).isEqualTo(DataScopePolicy.Decision.NO_FILTER);
    }

    @Test
    void decide_all_shouldReturnNoFilter() {
        DataScopePolicy.Decision decision = policy.decide(
                UUID.randomUUID(), UUID.randomUUID(), DataScope.ALL);
        assertThat(decision).isEqualTo(DataScopePolicy.Decision.NO_FILTER);
    }

    @Test
    void decide_nullDataScope_shouldReturnNoFilter() {
        DataScopePolicy.Decision decision = policy.decide(UUID.randomUUID(), UUID.randomUUID(), null);
        assertThat(decision).isEqualTo(DataScopePolicy.Decision.NO_FILTER);
    }

    // ===== buildFilter() 便捷方法默认列名 =====

    @Test
    void buildFilter_noColumnArgs_shouldUseDefaultColumnNames() {
        UUID userId = UUID.randomUUID();
        String result = policy.buildFilter(userId, null, DataScope.PERSONAL);
        // 默认使用 "user_id" 和 "dept_id"
        assertThat(result).isEqualTo("user_id = ?");
    }
}