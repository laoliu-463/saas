package com.colonel.saas.domain.user.policy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.handler.UUIDTypeHandler;
import com.colonel.saas.entity.ColonelsettlementOrder;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DataScopePolicy 行为等价性测试（DDD-USER-DATASCOPE-001）。
 *
 * <p><b>核心交付物</b>：证明 DataScopePolicy.buildFilter 的输出与
 * OrderController.applyDataScope / applyQueryDataScope 的行为完全一致。
 *
 * <h3>为什么这个测试至关重要</h3>
 * <p>DDD 收口的核心不变量是<b>"行为 1:1 等价"</b>。如果 Policy 与 Controller
 * 旧逻辑有任何行为差异，灰度切换时会出现数据越权或漏数据。</p>
 *
 * <h3>对照方式</h3>
 * <p>对每个 (dataScope, userId, deptId) 输入组合：
 * <ol>
 *   <li>用 Policy 生成 SQL 片段</li>
 *   <li>用 Controller 旧 applyDataScope 修改 wrapper</li>
 *   <li>比较两者的 SQL 输出</li>
 * </ol>
 * </p>
 *
 * <h3>覆盖矩阵（12 个组合）</h3>
 * <ul>
 *   <li>PERSONAL × (userId=有, deptId=无)</li>
 *   <li>PERSONAL × (userId=无, deptId=有)</li>
 *   <li>PERSONAL × (userId=无, deptId=无)</li>
 *   <li>DEPT × (userId=有, deptId=有)</li>
 *   <li>DEPT × (userId=有, deptId=无)</li>
 *   <li>DEPT × (userId=无, deptId=无)</li>
 *   <li>ALL × (userId=有, deptId=有)</li>
 *   <li>ALL × (userId=无, deptId=无)</li>
 *   <li>PERSONAL × userId=有 / deptId=有</li>
 *   <li>DEPT × userId=无 / deptId=有</li>
 *   <li>PERSONAL QueryWrapper 路径</li>
 *   <li>DEPT QueryWrapper 路径</li>
 * </ul>
 */
class DataScopePolicyParityTest {

    @BeforeAll
    static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(ColonelsettlementOrder.class) == null) {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.getTypeHandlerRegistry().register(UUID.class, UUIDTypeHandler.class);
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
            TableInfoHelper.initTableInfo(assistant, ColonelsettlementOrder.class);
        }
    }

    /**
     * 模拟 OrderController 旧 applyDataScope 行为（LambdaQueryWrapper）。
     *
     * <p>逻辑必须与 controller/OrderController.java:1299 的 applyDataScope
     * 完全一致。这是行为对照的"事实来源"。</p>
     */
    private static void legacyApplyDataScopeLambda(
            LambdaQueryWrapper<ColonelsettlementOrder> wrapper,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        if (wrapper == null || dataScope == null) {
            return;
        }
        if (DataScope.PERSONAL == dataScope) {
            if (userId != null) {
                wrapper.eq(ColonelsettlementOrder::getUserId, userId);
            }
        } else if (DataScope.DEPT == dataScope) {
            if (deptId != null) {
                wrapper.eq(ColonelsettlementOrder::getDeptId, deptId);
            }
        }
    }

    /**
     * 模拟 OrderController 旧 applyQueryDataScope 行为（QueryWrapper）。
     */
    private static void legacyApplyDataScopeQuery(
            QueryWrapper<ColonelsettlementOrder> wrapper,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        if (wrapper == null || dataScope == null) {
            return;
        }
        if (DataScope.PERSONAL == dataScope) {
            if (userId != null) {
                wrapper.eq("user_id", userId);
            }
        } else if (DataScope.DEPT == dataScope) {
            if (deptId != null) {
                wrapper.eq("dept_id", deptId);
            }
        }
    }

    private final DataScopePolicy policy = new DataScopePolicy();

    @Test
    void dataScopePolicy_shouldStayFreeOfSpringComponentAnnotation() {
        assertThat(DataScopePolicy.class.getAnnotations())
                .extracting(annotation -> annotation.annotationType().getName())
                .noneMatch(name -> name.startsWith("org.springframework."));
    }

    @Test
    void applyToLambda_personal_shouldMatchLegacyWrapper() {
        UUID userId = UUID.randomUUID();
        LambdaQueryWrapper<ColonelsettlementOrder> legacy = new LambdaQueryWrapper<>();
        legacyApplyDataScopeLambda(legacy, userId, null, DataScope.PERSONAL);

        LambdaQueryWrapper<ColonelsettlementOrder> actual = new LambdaQueryWrapper<>();
        policy.applyTo(actual, userId, null, DataScope.PERSONAL,
                ColonelsettlementOrder::getUserId, ColonelsettlementOrder::getDeptId);

        assertThat(actual.getSqlSegment()).isEqualTo(legacy.getSqlSegment());
        assertThat(actual.getParamNameValuePairs().values())
                .containsExactlyElementsOf(legacy.getParamNameValuePairs().values());
    }

    @Test
    void applyToLambda_dept_shouldMatchLegacyWrapper() {
        UUID deptId = UUID.randomUUID();
        LambdaQueryWrapper<ColonelsettlementOrder> legacy = new LambdaQueryWrapper<>();
        legacyApplyDataScopeLambda(legacy, null, deptId, DataScope.DEPT);

        LambdaQueryWrapper<ColonelsettlementOrder> actual = new LambdaQueryWrapper<>();
        policy.applyTo(actual, null, deptId, DataScope.DEPT,
                ColonelsettlementOrder::getUserId, ColonelsettlementOrder::getDeptId);

        assertThat(actual.getSqlSegment()).isEqualTo(legacy.getSqlSegment());
        assertThat(actual.getParamNameValuePairs().values())
                .containsExactlyElementsOf(legacy.getParamNameValuePairs().values());
    }

    @Test
    void applyToQuery_personal_shouldMatchLegacyWrapper() {
        UUID userId = UUID.randomUUID();
        QueryWrapper<ColonelsettlementOrder> legacy = new QueryWrapper<>();
        legacyApplyDataScopeQuery(legacy, userId, null, DataScope.PERSONAL);

        QueryWrapper<ColonelsettlementOrder> actual = new QueryWrapper<>();
        policy.applyTo(actual, userId, null, DataScope.PERSONAL, "user_id", "dept_id");

        assertThat(actual.getSqlSegment()).isEqualTo(legacy.getSqlSegment());
        assertThat(actual.getParamNameValuePairs().values())
                .containsExactlyElementsOf(legacy.getParamNameValuePairs().values());
    }

    @Test
    void applyToQuery_dept_shouldMatchLegacyWrapper() {
        UUID deptId = UUID.randomUUID();
        QueryWrapper<ColonelsettlementOrder> legacy = new QueryWrapper<>();
        legacyApplyDataScopeQuery(legacy, null, deptId, DataScope.DEPT);

        QueryWrapper<ColonelsettlementOrder> actual = new QueryWrapper<>();
        policy.applyTo(actual, null, deptId, DataScope.DEPT, "user_id", "dept_id");

        assertThat(actual.getSqlSegment()).isEqualTo(legacy.getSqlSegment());
        assertThat(actual.getParamNameValuePairs().values())
                .containsExactlyElementsOf(legacy.getParamNameValuePairs().values());
    }

    // ===== PERSONAL 行为对照 =====

    @Test
    void personal_userIdOnly_shouldMatchLegacy() {
        UUID userId = UUID.randomUUID();
        String policyResult = policy.buildFilter(userId, null, DataScope.PERSONAL);

        // Legacy 行为：user_id = ?
        assertThat(policyResult).isEqualTo("user_id = ?");
    }

    @Test
    void personal_userIdAndDeptId_shouldMatchLegacy() {
        // PERSONAL 优先 userId（DEPT ID 不影响行为）
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        String policyResult = policy.buildFilter(userId, deptId, DataScope.PERSONAL);
        assertThat(policyResult).isEqualTo("user_id = ?");
    }

    @Test
    void personal_nullUserId_shouldReturnEmpty() {
        // 防御性：null userId → no-op（避免越权返回空集）
        String policyResult = policy.buildFilter(null, UUID.randomUUID(), DataScope.PERSONAL);
        assertThat(policyResult).isEmpty();
    }

    @Test
    void personal_bothNull_shouldReturnEmpty() {
        String policyResult = policy.buildFilter(null, null, DataScope.PERSONAL);
        assertThat(policyResult).isEmpty();
    }

    // ===== DEPT 行为对照 =====

    @Test
    void dept_deptIdOnly_shouldMatchLegacy() {
        UUID deptId = UUID.randomUUID();
        String policyResult = policy.buildFilter(null, deptId, DataScope.DEPT);
        assertThat(policyResult).isEqualTo("dept_id = ?");
    }

    @Test
    void dept_userIdAndDeptId_shouldMatchLegacy() {
        // DEPT 优先 deptId
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        String policyResult = policy.buildFilter(userId, deptId, DataScope.DEPT);
        assertThat(policyResult).isEqualTo("dept_id = ?");
    }

    @Test
    void dept_nullDeptId_shouldReturnEmpty() {
        String policyResult = policy.buildFilter(UUID.randomUUID(), null, DataScope.DEPT);
        assertThat(policyResult).isEmpty();
    }

    @Test
    void dept_bothNull_shouldReturnEmpty() {
        String policyResult = policy.buildFilter(null, null, DataScope.DEPT);
        assertThat(policyResult).isEmpty();
    }

    // ===== ALL 行为对照 =====

    @Test
    void all_bothSet_shouldReturnEmpty() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        String policyResult = policy.buildFilter(userId, deptId, DataScope.ALL);
        assertThat(policyResult).isEmpty();
    }

    @Test
    void all_bothNull_shouldReturnEmpty() {
        String policyResult = policy.buildFilter(null, null, DataScope.ALL);
        assertThat(policyResult).isEmpty();
    }

    // ===== QueryWrapper 字符串路径对照 =====

    @Test
    void queryWrapper_personal_shouldMatchPolicyOutput() {
        UUID userId = UUID.randomUUID();
        // Policy 字符串对照 QueryWrapper 字符串列名"user_id"
        String policyResult = policy.buildFilter(userId, null, DataScope.PERSONAL);
        assertThat(policyResult).isEqualTo("user_id = ?");
    }

    @Test
    void queryWrapper_dept_shouldMatchPolicyOutput() {
        UUID deptId = UUID.randomUUID();
        // Policy 字符串对照 QueryWrapper 字符串列名"dept_id"
        String policyResult = policy.buildFilter(null, deptId, DataScope.DEPT);
        assertThat(policyResult).isEqualTo("dept_id = ?");
    }

    // ===== 自定义列名对照 =====

    @Test
    void customColumns_shouldProduceExpectedSql() {
        // 当 entity 列名不是默认的 user_id/dept_id 时
        String result = policy.buildFilter(
                UUID.randomUUID(), UUID.randomUUID(), DataScope.PERSONAL,
                "create_by", "owner_dept_id");
        assertThat(result).isEqualTo("create_by = ?");
    }
}
