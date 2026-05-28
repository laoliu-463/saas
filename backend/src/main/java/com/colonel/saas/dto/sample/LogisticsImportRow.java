package com.colonel.saas.dto.sample;

import lombok.Builder;
import lombok.Value;

/**
 * 物流导入行数据 DTO。
 * <p>
 * 表示物流信息批量导入文件中的一行原始数据，包含寄样关联信息和物流追踪信息。
 * 关联业务领域：寄样域（Sample）。
 * </p>
 */
@Value
@Builder
public class LogisticsImportRow {
    /** 原始行号 */
    int rowNo;
    /** 寄样申请 ID */
    String sampleRequestId;
    /** 寄样单号 */
    String sampleNo;
    /** 商品 ID */
    String productId;
    /** 达人账号标识 */
    String talentAccount;
    /** 快递公司名称 */
    String logisticsCompany;
    /** 运单号 */
    String trackingNo;
    /** 备注信息 */
    String remark;
}
