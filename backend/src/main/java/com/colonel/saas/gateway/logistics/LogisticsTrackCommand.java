package com.colonel.saas.gateway.logistics;

import lombok.Builder;
import lombok.Value;

/**
 * 物流轨迹查询命令对象。
 *
 * <p>功能描述：封装查询物流轨迹所需的全部参数，支持快递公司编码、单号、手机号、
 * 出发地/目的地等可选信息。不同物流服务商对参数的要求不同：
 * <ul>
 *   <li>快递鸟（Kdniao）：仅需 companyCode + trackingNo</li>
 *   <li>快递100（Kuaidi100）：顺丰（SF）和中通（ZTO）等需要 phone 参数做身份验证</li>
 *   <li>resultV2：快递100订阅推送时返回的旧版结果标识，用于增量查询</li>
 * </ul>
 * </p>
 *
 * <p>环境说明：使用 Lombok {@code @Value @Builder} 生成不可变对象与构建器，
 * real 和 test 环境均可使用。</p>
 *
 * <p>所属业务领域：寄样域 / 物流适配层</p>
 *
 * @see LogisticsGateway#queryTrack(LogisticsTrackCommand)
 */
@Value
@Builder
public class LogisticsTrackCommand {
    /** 快递公司编码（如 SF、ZTO、YD 等） */
    String companyCode;
    /** 快递单号 */
    String trackingNo;
    /** 收件人手机号（部分快递公司查询时需要，如顺丰、中通） */
    String phone;
    /** 出发地（可选，用于快递100订阅） */
    String from;
    /** 目的地（可选，用于快递100订阅） */
    String to;
    /** 快递100旧版结果标识（可选，用于增量轨迹查询） */
    String resultV2;

    /**
     * 快捷构造方法，仅指定公司编码和单号。
     *
     * @param companyCode 快递公司编码
     * @param trackingNo  快递单号
     * @return 构建好的 LogisticsTrackCommand 实例
     */
    public static LogisticsTrackCommand of(String companyCode, String trackingNo) {
        return LogisticsTrackCommand.builder()
                .companyCode(companyCode)
                .trackingNo(trackingNo)
                .build();
    }
}
