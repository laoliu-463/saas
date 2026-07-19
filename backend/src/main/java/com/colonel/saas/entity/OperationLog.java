package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 通用操作审计日志实体。
 * <p>
 * 对应数据库表：{@code operation_log}，记录系统中所有用户操作的完整审计轨迹。
 * 涵盖 HTTP 请求/响应、操作模块、操作目标、执行耗时、错误信息等维度，
 * 用于安全审计、问题排查和操作回溯。不继承 BaseEntity，采用手动输入的 UUID 主键
 * 和独立的逻辑删除字段。
 * </p>
 *
 * @see ProductOperationLog 商品操作专用日志
 */
@Data
@TableName(value = "operation_log", autoResultMap = true)
public class OperationLog {

    /**
     * 主键 ID
     * <p>手动输入的 UUID 主键</p>
     */
    @TableId(type = IdType.INPUT)
    private UUID id;

    /**
     * 操作人 ID
     * <p>执行本次操作的系统用户标识</p>
     */
    private UUID userId;

    /**
     * 操作人用户名
     * <p>冗余存储操作人登录名，便于日志展示，避免关联用户表</p>
     */
    private String username;

    /**
     * 操作模块
     * <p>标识操作所属的业务模块，如 "TALENT"（达人管理）、"PRODUCT"（商品管理）、
     * "SAMPLE"（寄样管理）、"PERFORMANCE"（业绩管理）等</p>
     */
    private String module;

    /**
     * 操作动作
     * <p>标识具体的操作行为，如 "CREATE"（创建）、"UPDATE"（更新）、"DELETE"（删除）、
     * "EXPORT"（导出）、"IMPORT"（导入）等</p>
     */
    private String action;

    /**
     * 操作目标类型
     * <p>标识被操作对象的实体类型，如 "Talent"、"Product"、"SampleRequest" 等</p>
     */
    private String targetType;

    /**
     * 操作目标 ID
     * <p>被操作对象的主键标识，可为 UUID 或其他业务主键格式</p>
     */
    private String targetId;

    /**
     * 操作目标名称
     * <p>被操作对象的显示名称，便于审计日志的可读性展示</p>
     */
    private String targetName;

    /**
     * 操作内容描述
     * <p>操作的自然语言描述，如"修改达人联系方式"、"导出订单列表"等</p>
     */
    private String content;

    /**
     * HTTP 请求方法
     * <p>对应的 HTTP 方法，如 "GET"、"POST"、"PUT"、"DELETE" 等</p>
     */
    private String requestMethod;

    /**
     * HTTP 请求 URL
     * <p>操作对应的请求路径</p>
     */
    private String requestUrl;

    /**
     * 请求查询参数
     * <p>JSON 格式，记录 HTTP 请求的 URL 查询参数，由 JacksonTypeHandler 自动序列化/反序列化</p>
     */
    @TableField(value = "request_params", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> requestParams;

    /**
     * 请求体
     * <p>JSON 格式，记录 HTTP 请求的 Body 参数，由 JacksonTypeHandler 自动序列化/反序列化</p>
     */
    @TableField(value = "request_body", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> requestBody;

    /**
     * HTTP 响应状态码
     * <p>操作对应的 HTTP 响应码，如 "200"、"400"、"500" 等</p>
     */
    private String responseCode;

    /**
     * HTTP 响应体
     * <p>JSON 格式，记录 HTTP 响应的数据内容，由 JacksonTypeHandler 自动序列化/反序列化</p>
     */
    @TableField(value = "response_body", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> responseBody;

    /**
     * 客户端 IP 地址
     * <p>操作人的客户端 IP，用于安全审计和异常访问检测</p>
     */
    private String ipAddress;

    /**
     * User-Agent
     * <p>操作人客户端的浏览器/设备标识信息</p>
     */
    private String userAgent;

    /**
     * 执行耗时（毫秒）
     * <p>操作从开始到结束的耗时，用于性能监控和慢操作告警</p>
     */
    private Long durationMs;

    /** 结构化错误码，成功操作时为 null。 */
    private String errorCode;

    /**
     * 错误信息
     * <p>操作执行失败时的错误描述，成功操作时为 null</p>
     */
    private String errorMessage;

    /** 请求或后台任务链路 ID，用于跨日志检索。 */
    private String traceId;

    /**
     * 创建时间
     * <p>操作日志的记录时间</p>
     */
    private LocalDateTime createTime;

    /**
     * 逻辑删除标记
     * <p>0=未删除, 1=已删除。审计日志通常不做物理删除</p>
     */
    private Integer deleted;
}
