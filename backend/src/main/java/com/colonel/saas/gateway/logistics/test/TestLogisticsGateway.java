package com.colonel.saas.gateway.logistics.test;

import com.colonel.saas.gateway.logistics.LogisticsGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 测试/模拟物流网关，仅在 {@code app.test.enabled=true} 时激活。
 * <p>
 * 本类实现 {@link LogisticsGateway} 接口，用于本地开发、单元测试和 test 环境下
 * 模拟物流服务的行为，避免在非真实环境调用第三方物流 API。
 * </p>
 *
 * <ul>
 *   <li>根据运单号中的关键字（FAILED / EXCEPTION / IN_TRANSIT / NO_TRACE）模拟不同的物流状态</li>
 *   <li>默认返回已签收（SIGNED）状态，模拟正常签收流程</li>
 *   <li>生成带有日期和哈希后缀的演示运单号（TEST-SF-yyyyMMdd-xxxx）</li>
 *   <li>通过 {@link ConditionalOnProperty} 条件注入，不影响生产环境</li>
 * </ul>
 *
 * <p>所属领域：物流域 / 测试基础设施</p>
 *
 * @see LogisticsGateway 物流网关抽象接口
 * @see com.colonel.saas.service.LogisticsGatewayHealthService 物流网关健康检查服务
 */
@Component
@ConditionalOnProperty(prefix = "app.test", name = "enabled", havingValue = "true")
public class TestLogisticsGateway implements LogisticsGateway {

    /** 日期格式化器，用于生成运单号中的日期部分（yyyyMMdd） */
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 创建模拟物流发货单。
     * <p>
     * 处理流程：
     * <ol>
     *   <li>获取当前时间作为发货时间</li>
     *   <li>根据寄样请求 ID 的哈希值生成 4 位数字后缀，保证可重现性</li>
     *   <li>拼接运单号：TEST-SF-{日期}-{后缀}</li>
     *   <li>返回演示物流单号和"SHIPPING"状态</li>
     * </ol>
     *
     * @param command 物流发货指令，包含寄样请求 ID、收发地址等信息
     * @return 物流发货结果，包含演示运单号、物流公司名称、状态和发货时间
     */
    @Override
    public LogisticsResult createShipment(LogisticsCommand command) {
        // 第一步：获取当前时间，用于生成运单号日期和发货时间
        LocalDateTime now = LocalDateTime.now();
        // 第二步：基于寄样请求 ID 的哈希值取模生成 4 位数字后缀
        String suffix = String.format("%04d", Math.abs(command.sampleRequestId().hashCode()) % 10_000);
        // 第三步：拼接演示运单号，格式为 TEST-SF-yyyyMMdd-xxxx
        String trackingNo = "TEST-SF-" + now.format(DATE) + "-" + suffix;
        return new LogisticsResult(trackingNo, "演示物流-顺丰模拟", "SHIPPING", now);
    }

    /**
     * 查询模拟物流状态。
     * <p>
     * 处理流程：
     * <ol>
     *   <li>委托 {@link #queryTrack} 方法查询详细轨迹</li>
     *   <li>将轨迹结果转换为简化的状态结果</li>
     *   <li>缺失原因时使用默认提示"演示物流状态已更新"</li>
     *   <li>缺失签收时间时使用当前时间</li>
     * </ol>
     *
     * @param trackingNo 运单号，其中的关键字决定模拟的物流状态
     * @return 物流状态查询结果，包含运单号、物流公司、内部状态、原因和签收时间
     */
    @Override
    public LogisticsStatusResult queryStatus(String trackingNo) {
        // 委托 queryTrack 获取详细轨迹，再转换为状态结果
        LogisticsTrackResult track = queryTrack("SF", trackingNo);
        return new LogisticsStatusResult(
                trackingNo,
                "演示物流-顺丰模拟",
                track.internalStatus(),
                track.reason() == null ? "演示物流状态已更新" : track.reason(),
                track.signedAt() == null ? LocalDateTime.now() : track.signedAt()
        );
    }

    /**
     * 查询模拟物流轨迹详情。
     * <p>
     * 处理流程：
     * <ol>
     *   <li>标准化物流公司编码（空值默认 "SF"）</li>
     *   <li>根据运单号中的关键字匹配模拟场景：
     *     <ul>
     *       <li>{@code FAILED} — 模拟查询失败</li>
     *       <li>{@code EXCEPTION} — 模拟运输异常（状态码 4）</li>
     *       <li>{@code IN_TRANSIT} — 模拟运输中（状态码 2）</li>
     *       <li>{@code NO_TRACE} — 模拟暂无轨迹</li>
     *       <li>其他 — 默认模拟已签收（状态码 3）</li>
     *     </ul>
     *   </li>
     *   <li>构造对应的轨迹节点和元数据返回</li>
     * </ol>
     *
     * @param companyCode 物流公司编码（如 SF、YTO 等），空值时默认为 "SF"
     * @param trackingNo  运单号，其中的关键字决定模拟的物流状态
     * @return 物流轨迹查询结果，包含状态、轨迹节点列表和原始元数据
     */
    @Override
    public LogisticsTrackResult queryTrack(String companyCode, String trackingNo) {
        LocalDateTime now = LocalDateTime.now();
        // 第一步：标准化物流公司编码，空值或空白时默认使用顺丰（SF）
        String normalizedCompany = companyCode == null || companyCode.isBlank() ? "SF" : companyCode;

        // 第二步：根据运单号中的关键字匹配对应的模拟场景
        // 场景 1：运单号包含 "FAILED"，模拟查询失败
        if (trackingNo != null && trackingNo.contains("FAILED")) {
            return new LogisticsTrackResult(
                    normalizedCompany,
                    trackingNo,
                    false,
                    "模拟查询失败",
                    null,
                    "FAILED",
                    false,
                    null,
                    List.of(),
                    Map.of("Success", false));
        }
        // 场景 2：运单号包含 "EXCEPTION"，模拟运输异常（状态码 4）
        if (trackingNo != null && trackingNo.contains("EXCEPTION")) {
            return new LogisticsTrackResult(
                    normalizedCompany,
                    trackingNo,
                    true,
                    null,
                    "4",
                    "EXCEPTION",
                    false,
                    null,
                    List.of(new LogisticsTraceNode(now.minusHours(1), "快件运输异常，等待人工处理", null)),
                    Map.of("Success", true, "State", "4"));
        }
        // 场景 3：运单号包含 "IN_TRANSIT"，模拟运输中（状态码 2）
        if (trackingNo != null && trackingNo.contains("IN_TRANSIT")) {
            return new LogisticsTrackResult(
                    normalizedCompany,
                    trackingNo,
                    true,
                    null,
                    "2",
                    "IN_TRANSIT",
                    false,
                    null,
                    List.of(new LogisticsTraceNode(now.minusHours(2), "快件已揽收，正在运输中", null)),
                    Map.of("Success", true, "State", "2"));
        }
        // 场景 4：运单号包含 "NO_TRACE"，模拟暂无轨迹信息
        if (trackingNo != null && trackingNo.contains("NO_TRACE")) {
            return new LogisticsTrackResult(
                    normalizedCompany,
                    trackingNo,
                    true,
                    "暂无轨迹信息",
                    null,
                    "NO_TRACE",
                    false,
                    null,
                    List.of(),
                    Map.of("Success", true));
        }
        // 场景 5（默认）：模拟正常签收（状态码 3），签收地点为演示城市
        return new LogisticsTrackResult(
                normalizedCompany,
                trackingNo,
                true,
                null,
                "3",
                "SIGNED",
                true,
                now,
                List.of(new LogisticsTraceNode(now, "派件已签收[演示城市]", null)),
                Map.of("Success", true, "State", "3"));
    }
}
