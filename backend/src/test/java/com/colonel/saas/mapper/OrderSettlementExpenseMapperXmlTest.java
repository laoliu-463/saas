package com.colonel.saas.mapper;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OrderSettlementExpenseMapperXmlTest {

    @Test
    void colonelSettlementOrderMapper_shouldPersistServiceFeeExpenseColumns() throws Exception {
        String xml = Files.readString(Path.of("src/main/resources/mapper/ColonelsettlementOrderMapper.xml"));

        assertThat(xml).contains("property=\"estimateServiceFeeExpense\" column=\"estimate_service_fee_expense\"");
        assertThat(xml).contains("property=\"effectiveServiceFeeExpense\" column=\"effective_service_fee_expense\"");
        assertThat(xml).contains("estimate_service_fee_expense, effective_service_fee_expense");
        assertThat(xml).contains("estimate_service_fee_expense = #{estimateServiceFeeExpense}");
        assertThat(xml).contains("effective_service_fee_expense = #{effectiveServiceFeeExpense}");
    }

    @Test
    void performanceRecordMapper_shouldPersistServiceFeeExpenseColumns() throws Exception {
        String xml = Files.readString(Path.of("src/main/resources/mapper/PerformanceRecordMapper.xml"));

        assertThat(xml).contains("property=\"estimateServiceFeeExpense\" column=\"estimate_service_fee_expense\"");
        assertThat(xml).contains("property=\"effectiveServiceFeeExpense\" column=\"effective_service_fee_expense\"");
        assertThat(xml).contains("estimate_service_fee_expense, effective_service_fee_expense");
        assertThat(xml).contains("estimate_service_fee_expense = EXCLUDED.estimate_service_fee_expense");
        assertThat(xml).contains("effective_service_fee_expense = EXCLUDED.effective_service_fee_expense");
    }
}
