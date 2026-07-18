package com.colonel.saas.architecture;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.testsupport.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThatCode;

/** 真实 PostgreSQL 上编译并执行订单核心 Mapper SQL，避免静态字符串测试漏掉缺列。 */
class ColonelsettlementOrderMapperSchemaIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ColonelsettlementOrderMapper mapper;

    @Test
    void corePageQueryShouldExecuteAgainstMigratedSchema() {
        assertThatCode(() -> mapper.findPageWithScope(
                new Page<ColonelsettlementOrder>(1, 10),
                new QueryWrapper<>()))
                .doesNotThrowAnyException();
    }
}
