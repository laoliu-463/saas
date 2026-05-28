package com.colonel.saas.gateway.logistics.query;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mock 物流查询网关实现。
 * <p>
 * 用于 test 环境，不请求真实外部 API。
 * 根据运单号前缀模拟不同的物流状态，方便开发和测试。
 * </p>
 *
 * <h3>运单号前缀规则</h3>
 * <ul>
 *   <li>{@code FAIL / FAILED} - 模拟查询失败</li>
 *   <li>{@code TRANSIT / IN_TRANSIT} - 模拟运输中</li>
 *   <li>{@code DELIVER / DELIVERING} - 模拟派送中</li>
 *   <li>{@code REJECT / REJECTED} - 模拟拒收</li>
 *   <li>{@code SIGN / SIGNED / MOCK} - 模拟已签收</li>
 *   <li>其他前缀 - 默认返回运输中</li>
 * </ul>
 */
@Component
public class MockLogisticsQueryGateway implements LogisticsQueryGateway {

    /**
     * 根据运单号前缀返回模拟的物流查询结果。
     *
     * @param logisticsCompany 快递公司编码（为空时默认 "MOCK"）
     * @param trackingNo       物流运单号（根据前缀决定模拟状态）
     * @return 模拟的物流查询结果
     */
    @Override
    public LogisticsQueryResult query(String logisticsCompany, String trackingNo) {
        String company = StringUtils.hasText(logisticsCompany) ? logisticsCompany.trim() : "MOCK";
        String no = trackingNo == null ? "" : trackingNo.trim();
        if (!StringUtils.hasText(no)) {
            return LogisticsQueryResult.queryFailed(providerName(), company, no, "INVALID_PARAM", "物流单号不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        String upper = no.toUpperCase(Locale.ROOT);
        if (upper.startsWith("FAIL") || upper.contains("FAILED")) {
            return LogisticsQueryResult.queryFailed(providerName(), company, no, "MOCK_FAILED", "模拟查询失败");
        }
        if (upper.startsWith("TRANSIT") || upper.contains("IN_TRANSIT")) {
            return buildSuccess(company, no, LogisticsStatusCode.IN_TRANSIT, "运输中", false, null,
                    List.of(trace(now.minusHours(2), "快件已揽收，运输中", "演示城市")));
        }
        if (upper.startsWith("DELIVER") || upper.contains("DELIVERING")) {
            return buildSuccess(company, no, LogisticsStatusCode.DELIVERING, "派送中", false, null,
                    List.of(trace(now.minusHours(1), "快件派送中", "演示城市")));
        }
        if (upper.startsWith("REJECT") || upper.contains("REJECTED")) {
            return buildSuccess(company, no, LogisticsStatusCode.REJECTED, "拒收", false, null,
                    List.of(trace(now, "快件被拒收", "演示城市")));
        }
        if (upper.startsWith("SIGN") || upper.contains("SIGNED") || upper.startsWith("MOCK")) {
            return buildSuccess(company, no, LogisticsStatusCode.SIGNED, "已签收", true, now,
                    List.of(trace(now, "派件已签收[演示]", "演示城市")));
        }
        return buildSuccess(company, no, LogisticsStatusCode.IN_TRANSIT, "运输中", false, null,
                List.of(trace(now.minusHours(1), "快件运输中", "演示城市")));
    }

    /** Mock 实现始终可用 */
    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public String providerName() {
        return "MOCK";
    }

    /**
     * 构建成功的查询结果。
     *
     * @param company    快递公司名称
     * @param trackingNo 运单号
     * @param statusCode 统一状态码
     * @param statusName 人类可读状态名
     * @param signed     是否已签收
     * @param signedAt   签收时间（未签收时为 null）
     * @param traces     轨迹节点列表
     * @return 构建完成的查询结果
     */
    private LogisticsQueryResult buildSuccess(
            String company,
            String trackingNo,
            LogisticsStatusCode statusCode,
            String statusName,
            boolean signed,
            LocalDateTime signedAt,
            List<LogisticsQueryResult.LogisticsTraceItem> traces) {
        return LogisticsQueryResult.builder()
                .success(true)
                .provider(providerName())
                .trackingNo(trackingNo)
                .logisticsCompany(company)
                .statusCode(statusCode)
                .statusName(statusName)
                .signed(signed)
                .signedAt(signedAt)
                .traces(traces)
                .rawPayload(Map.of("mock", true, "provider", providerName()))
                .queriedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 构建单条轨迹节点。
     *
     * @param time     轨迹时间
     * @param content  轨迹描述
     * @param location 轨迹地点
     * @return 轨迹节点
     */
    private LogisticsQueryResult.LogisticsTraceItem trace(LocalDateTime time, String content, String location) {
        return LogisticsQueryResult.LogisticsTraceItem.builder()
                .traceTime(time)
                .traceContent(content)
                .location(location)
                .build();
    }
}
