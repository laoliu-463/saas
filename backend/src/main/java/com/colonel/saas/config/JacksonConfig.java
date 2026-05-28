package com.colonel.saas.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.TimeZone;

/**
 * Jackson 全局序列化/反序列化配置。
 * <p>
 * 自定义 {@link ObjectMapper} 的行为，用于统一项目中所有 JSON 序列化规则：
 * <ul>
 *   <li>注册 Java 8 时间模块（{@link JavaTimeModule}），支持 {@code LocalDateTime}、{@code LocalDate} 等类型</li>
 *   <li>禁用时间戳格式，日期以 ISO-8601 字符串形式输出（如 {@code "2026-05-27T10:00:00"}）</li>
 *   <li>排除 {@code null} 值字段，减少 API 响应体积</li>
 *   <li>统一使用 Asia/Shanghai 时区，避免不同服务器时区差异导致的时间偏差</li>
 * </ul>
 *
 * <p>应用场景：REST API 响应序列化、Redis 缓存对象序列化、日志输出等所有 JSON 操作。</p>
 */
@Configuration
public class JacksonConfig {

    /**
     * 创建并配置全局 ObjectMapper 实例。
     * <p>
     * 覆盖 Spring Boot 默认的 ObjectMapper 配置，确保所有通过 Spring MVC
     * 或手动注入的 {@code ObjectMapper} 使用统一的序列化策略。
     * </p>
     *
     * @param builder Spring 提供的 Jackson2ObjectMapperBuilder，用于读取已有自定义配置
     * @return 配置完成的全局 ObjectMapper 实例
     */
    @Bean
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        // 不创建 XML Mapper，仅处理 JSON
        ObjectMapper mapper = builder.createXmlMapper(false).build();
        // 注册 Java 8 时间类型模块，支持 LocalDateTime/LocalDate/LocalTime 等
        mapper.registerModule(new JavaTimeModule());
        // 禁用将日期序列化为时间戳的默认行为，改为 ISO-8601 字符串格式
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 排除 null 值字段，减小响应体积，前端可按需处理缺失字段
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        // 统一时区为 Asia/Shanghai，确保时间字段在所有环境下表现一致
        mapper.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        return mapper;
    }
}
