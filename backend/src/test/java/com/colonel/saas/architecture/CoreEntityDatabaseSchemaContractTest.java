package com.colonel.saas.architecture;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.entity.ColonelsettlementActivity;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.PerformanceRecord;
import com.colonel.saas.entity.PickSourceMapping;
import com.colonel.saas.entity.Product;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.entity.PromotionLink;
import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.testsupport.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用真实 PostgreSQL information_schema 校验核心域实体字段。
 *
 * <p>范围覆盖用户、商品、订单、寄样、业绩和达人主链；新增核心实体时必须显式加入清单，
 * 防止仅修改 Java/Mapper 而漏掉数据库迁移。</p>
 */
class CoreEntityDatabaseSchemaContractTest extends BaseIntegrationTest {

    private static final List<Class<?>> CORE_ENTITIES = List.of(
            SysUser.class,
            Product.class,
            ProductSnapshot.class,
            ProductOperationState.class,
            ColonelsettlementActivity.class,
            ColonelsettlementOrder.class,
            SampleRequest.class,
            PerformanceRecord.class,
            Talent.class,
            PromotionLink.class,
            PickSourceMapping.class
    );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void coreEntityColumnsShouldExistInMigratedDatabase() {
        List<String> missing = new ArrayList<>();
        for (Class<?> entityType : CORE_ENTITIES) {
            TableName tableName = entityType.getAnnotation(TableName.class);
            assertThat(tableName).as(entityType.getSimpleName() + " must declare @TableName").isNotNull();
            Set<String> actualColumns = new LinkedHashSet<>(jdbcTemplate.queryForList(
                    "SELECT column_name FROM information_schema.columns "
                            + "WHERE table_schema = 'public' AND table_name = ?",
                    String.class, tableName.value()));
            assertThat(actualColumns).as("missing core table " + tableName.value()).isNotEmpty();
            entityColumns(entityType).stream()
                    .filter(column -> !actualColumns.contains(column))
                    .map(column -> tableName.value() + "." + column)
                    .forEach(missing::add);
        }
        assertThat(missing).as("entity columns missing from migrated database").isEmpty();
    }

    private static Set<String> entityColumns(Class<?> entityType) {
        Set<String> columns = new LinkedHashSet<>();
        for (Class<?> type = entityType; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                    continue;
                }
                TableField tableField = field.getAnnotation(TableField.class);
                if (tableField != null && !tableField.exist()) {
                    continue;
                }
                TableId tableId = field.getAnnotation(TableId.class);
                String explicit = tableId != null ? tableId.value()
                        : tableField != null ? tableField.value() : null;
                columns.add(StringUtils.hasText(explicit) ? explicit : camelToSnake(field.getName()));
            }
        }
        return columns;
    }

    private static String camelToSnake(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }
}
