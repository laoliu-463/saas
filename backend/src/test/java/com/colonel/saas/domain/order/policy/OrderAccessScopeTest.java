package com.colonel.saas.domain.order.policy;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.handler.UUIDTypeHandler;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionChecker;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import com.colonel.saas.entity.ColonelsettlementOrder;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderAccessScopeTest {

    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DEPT = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final CurrentUserPermissionChecker permissionChecker =
            new CurrentUserPermissionChecker(new CurrentUserPermissionPolicy());

    @BeforeEach
    void setUp() {
        if (TableInfoHelper.getTableInfo(ColonelsettlementOrder.class) == null) {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.getTypeHandlerRegistry().register(UUID.class, UUIDTypeHandler.class);
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ColonelsettlementOrder.class);
        }
    }

    @Test
    void pureBizStaff_shouldScopeOrderListByRecruiterAttribution() {
        LambdaQueryWrapper<ColonelsettlementOrder> wrapper = new LambdaQueryWrapper<>();

        OrderAccessScope.applyTo(
                wrapper,
                context(List.of(RoleCodes.BIZ_STAFF), DataScope.PERSONAL),
                permissionChecker);

        assertThat(wrapper.getSqlSegment()).contains("colonel_user_id");
        assertThat(wrapper.getSqlSegment()).doesNotContain(" user_id =");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(USER);
    }

    @Test
    void pureChannelStaff_shouldScopeStatsByChannelAttribution() {
        QueryWrapper<ColonelsettlementOrder> wrapper = new QueryWrapper<>();

        OrderAccessScope.applyTo(
                wrapper,
                context(List.of(RoleCodes.CHANNEL_STAFF), DataScope.PERSONAL),
                permissionChecker);

        assertThat(wrapper.getSqlSegment()).contains("channel_user_id");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(USER);
    }

    @Test
    void dualStaffRoles_shouldAllowEitherAttributionDimension() {
        LambdaQueryWrapper<ColonelsettlementOrder> wrapper = new LambdaQueryWrapper<>();

        OrderAccessScope.applyTo(
                wrapper,
                context(List.of(RoleCodes.BIZ_STAFF, RoleCodes.CHANNEL_STAFF), DataScope.PERSONAL),
                permissionChecker);

        assertThat(wrapper.getSqlSegment())
                .contains("channel_user_id")
                .contains("colonel_user_id")
                .contains("OR");
        assertThat(wrapper.getParamNameValuePairs().values()).containsExactlyInAnyOrder(USER, USER);
    }

    @Test
    void missingRoleContext_shouldPreserveLegacyPersonalScope() {
        LambdaQueryWrapper<ColonelsettlementOrder> wrapper = new LambdaQueryWrapper<>();

        OrderAccessScope.applyTo(
                wrapper,
                context(List.of(), DataScope.PERSONAL),
                permissionChecker);

        assertThat(wrapper.getSqlSegment()).contains("user_id");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(USER);
    }

    private OrderAccessContext context(List<String> roles, DataScope dataScope) {
        return new OrderAccessContext(USER, DEPT, dataScope, roles);
    }
}
