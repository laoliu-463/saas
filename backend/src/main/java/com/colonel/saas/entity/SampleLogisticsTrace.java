package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.colonel.saas.common.typehandler.JsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 寄样物流轨迹实体。
 * <p>
 * 对应数据库表：{@code sample_logistics_trace}，记录寄样包裹的物流追踪节点信息。
 * 不继承 BaseEntity（独立管理 ID 和时间字段），ID 由调用方指定（{@link IdType#ASSIGN_UUID}）。
 * 关联 {@link SampleRequest} 寄样请求，每条记录代表一个物流节点（揽收、中转、派送、签收等）。
 * 通过 nodeHash 实现物流节点去重，避免重复写入相同状态。
 * </p>
 *
 * @see SampleRequest 寄样请求实体
 */
@Data
@TableName(value = "sample_logistics_trace", autoResultMap = true)
public class SampleLogisticsTrace {

    /**
     * 主键 ID
     * <p>由调用方指定的 UUID 主键（{@link IdType#ASSIGN_UUID}）</p>
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    /**
     * 寄样请求 ID
     * <p>对应数据库列：{@code sample_request_id}，关联 {@link SampleRequest} 主键</p>
     */
    @TableField("sample_request_id")
    private UUID sampleRequestId;

    /**
     * 快递单号
     * <p>对应数据库列：{@code tracking_no}，物流包裹的快递单号，与寄样请求中的运单号对应</p>
     */
    @TableField("tracking_no")
    private String trackingNo;

    /**
     * 物流公司
     * <p>对应数据库列：{@code logistics_company}，承运的物流公司名称或编码</p>
     */
    @TableField("logistics_company")
    private String logisticsCompany;

    /**
     * 状态编码
     * <p>对应数据库列：{@code status_code}，物流状态的内部编码，
     * 如 "COLLECTED"（已揽收）、"IN_TRANSIT"（运输中）、"DELIVERING"（派送中）、"SIGNED"（已签收）</p>
     */
    @TableField("status_code")
    private String statusCode;

    /**
     * 状态名称
     * <p>对应数据库列：{@code status_name}，物流状态的中文显示名称</p>
     */
    @TableField("status_name")
    private String statusName;

    /**
     * 轨迹时间
     * <p>对应数据库列：{@code trace_time}，该物流节点实际发生的时间</p>
     */
    @TableField("trace_time")
    private LocalDateTime traceTime;

    /**
     * 轨迹内容
     * <p>对应数据库列：{@code trace_content}，物流节点的详细描述信息，
     * 如"已到达【XX转运中心】"</p>
     */
    @TableField("trace_content")
    private String traceContent;

    /**
     * 节点哈希
     * <p>对应数据库列：{@code node_hash}，物流节点的唯一指纹，
     * 基于 statusCode + traceTime + traceContent 计算，用于去重判断</p>
     */
    @TableField("node_hash")
    private String nodeHash;

    /**
     * 所在位置
     * <p>对应数据库列：{@code location}，物流节点所在的城市或站点信息</p>
     */
    @TableField("location")
    private String location;

    /**
     * 原始响应载荷
     * <p>JSON 对象格式，对应数据库列：{@code raw_payload}，
     * 存储物流 API 返回的完整原始数据，用于问题排查</p>
     */
    @TableField(value = "raw_payload", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> rawPayload;

    /**
     * 记录创建时间
     * <p>对应数据库列：{@code created_at}，物流轨迹记录的入库时间</p>
     */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
