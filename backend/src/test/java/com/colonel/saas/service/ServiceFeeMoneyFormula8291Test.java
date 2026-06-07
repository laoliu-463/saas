package com.colonel.saas.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DASHBOARD-MONEY-HIDDEN-DEDUCTION-8291-001：8291 样本口径公式回归。
 */
class ServiceFeeMoneyFormula8291Test {

    @Test
    void serviceFeeNetCent_shouldMatch8291TargetWhenExpensePresent() {
        long income = 327_702L;
        long tech = 27_949L;
        long expense = 190L;
        assertThat(CommissionService.serviceFeeNetCent(income, tech, expense)).isEqualTo(299_563L);
    }

    @Test
    void serviceFeeNetCent_shouldClose8291LocalVisibleFieldsWithoutHiddenDeduction() {
        long income = 327_618L;
        long tech = 27_949L;
        long expense = 0L;
        long profit = CommissionService.serviceFeeNetCent(income, tech, expense);
        assertThat(profit).isEqualTo(299_669L);
    }

    @Test
    void serviceFeeNetCent_expenseMustMatchProfitDeduction() {
        long income = 327_892L;
        long tech = 27_949L;
        long expense = 190L;
        long profit = CommissionService.serviceFeeNetCent(income, tech, expense);
        assertThat(income - tech - expense).isEqualTo(profit);
    }
}
