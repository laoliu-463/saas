package com.colonel.saas.gateway.logistics.query;

import com.colonel.saas.gateway.logistics.LogisticsTrackCommand;

/**
 * 统一物流查询网关接口（V2.0）。
 * <p>
 * 业务层通过 {@link LogisticsGatewayRouter} 路由调用，不直连快递鸟/快递100。
 * 此接口抽象了物流查询的核心能力，各 provider（快递鸟、快递100、Mock）
 * 各自实现，返回统一的 {@link LogisticsQueryResult}。
 * </p>
 *
 * <h3>与 LogisticsGateway 的关系</h3>
 * <p>
 * {@link com.colonel.saas.gateway.logistics.LogisticsGateway} 是 V1 版本的物流网关接口，
 * 直接返回 provider 特定的结果类型。本接口是 V2.0 统一查询网关，
 * 屏蔽 provider 差异，返回标准化的 {@link LogisticsQueryResult}。
 * </p>
 */
public interface LogisticsQueryGateway {

    /**
     * 查询物流轨迹。
     *
     * @param logisticsCompany 快递公司编码（如 SF、ZTO），部分 provider 需要
     * @param trackingNo       物流运单号
     * @return 统一格式的物流查询结果
     */
    LogisticsQueryResult query(String logisticsCompany, String trackingNo);

    /**
     * 查询物流轨迹（使用命令对象传参）。
     * <p>
     * 默认实现拆解 {@link LogisticsTrackCommand} 委托给 {@link #query(String, String)}。
     * </p>
     *
     * @param command 物流查询命令对象（包含公司编码、运单号、手机号等）
     * @return 统一格式的物流查询结果
     */
    default LogisticsQueryResult query(LogisticsTrackCommand command) {
        if (command == null) {
            return query(null, null);
        }
        return query(command.getCompanyCode(), command.getTrackingNo());
    }

    /**
     * 当前 provider 是否可用（凭证是否已配置）。
     *
     * @return true 表示凭证已配置，可正常查询
     */
    boolean isSupported();

    /**
     * provider 名称标识。
     *
     * @return 服务商标识字符串（如 KUAIDINIAO、KUAIDI100、MOCK）
     */
    String providerName();
}
