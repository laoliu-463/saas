package com.colonel.saas.common.typehandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Map;

/**
 * MyBatis JSONB 结构化对象类型处理器（typehandler 包版本）。
 *
 * <p>处理 PostgreSQL jsonb 列与 Java {@code Map<String, Object>} 的映射，
 * 使用 Jackson {@link ObjectMapper} 进行 JSON 序列化和反序列化。</p>
 *
 * <h3>使用场景</h3>
 * <p>当实体字段类型为 {@code Map<String, Object>}，且数据库列类型为 jsonb 时使用。
 * 适用于需要在 Java 层按 key 访问 JSON 内容的场景，如扩展配置、动态属性等。</p>
 *
 * <h3>与 handler 包版本的区别</h3>
 * <ul>
 *   <li><b>typehandler 包（本类）</b>：Map&lt;String, Object&gt; ↔ jsonb，适用于结构化 JSON 对象</li>
 *   <li><b>handler 包</b>：String ↔ jsonb，适用于原始 JSON 字符串存储</li>
 * </ul>
 *
 * <h3>错误处理</h3>
 * <ul>
 *   <li>序列化失败（Java → JSON）：抛出 {@link SQLException}，包含原始 {@link JsonProcessingException}</li>
 *   <li>反序列化失败（JSON → Java）：抛出 {@link SQLException}，包含原始异常</li>
 *   <li>JSON 根节点不是对象：抛出 {@link SQLException}（"JSONB value is not an object"）</li>
 *   <li>数据库值为 null 或空白字符串：返回 null</li>
 * </ul>
 *
 * @see com.colonel.saas.common.handler.JsonbTypeHandler 字符串版本的 JSONB 处理器
 * @see JsonbListTypeHandler 列表版本的 JSONB 处理器
 */
@MappedTypes({Map.class})
@MappedJdbcTypes(JdbcType.OTHER)
public class JsonbTypeHandler extends BaseTypeHandler<Map<String, Object>> {

    /** Jackson JSON 序列化/反序列化器（线程安全，全局共享） */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 设置非空 JSONB 参数（Java Map → SQL jsonb）。
     *
     * <p>将 Map 序列化为 JSON 字符串后，通过 {@code Types.OTHER} 写入 PostgreSQL 的 jsonb 列。</p>
     *
     * @param ps        PreparedStatement 对象
     * @param i         参数索引（从 1 开始）
     * @param parameter 待写入的 Map 对象
     * @param jdbcType  JDBC 类型
     * @throws SQLException 序列化失败时抛出
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Map<String, Object> parameter, JdbcType jdbcType)
            throws SQLException {
        try {
            ps.setObject(i, OBJECT_MAPPER.writeValueAsString(parameter), Types.OTHER);
        } catch (JsonProcessingException e) {
            throw new SQLException("Failed to serialize JSONB parameter", e);
        }
    }

    /**
     * 根据列名从 ResultSet 获取 JSONB 并解析为 Map。
     *
     * @param rs         ResultSet 对象
     * @param columnName 列名
     * @return 解析后的 Map，数据库为 NULL 或空字符串时返回 null
     * @throws SQLException 解析失败或 JSON 根节点非对象时抛出
     */
    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parseJson(rs.getString(columnName));
    }

    /**
     * 根据列索引从 ResultSet 获取 JSONB 并解析为 Map。
     *
     * @param rs          ResultSet 对象
     * @param columnIndex 列索引
     * @return 解析后的 Map，数据库为 NULL 或空字符串时返回 null
     * @throws SQLException 解析失败或 JSON 根节点非对象时抛出
     */
    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parseJson(rs.getString(columnIndex));
    }

    /**
     * 从 CallableStatement 获取 JSONB 并解析为 Map。
     *
     * @param cs          CallableStatement 对象
     * @param columnIndex 列索引
     * @return 解析后的 Map，数据库为 NULL 或空字符串时返回 null
     * @throws SQLException 解析失败或 JSON 根节点非对象时抛出
     */
    @Override
    public Map<String, Object> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parseJson(cs.getString(columnIndex));
    }

    /**
     * 将 JSON 字符串解析为 {@code Map<String, Object>}。
     *
     * <p>处理逻辑：</p>
     * <ol>
     *   <li>null 或空白字符串 → 返回 null</li>
     *   <li>解析为 Object，判断是否为 Map 实例</li>
     *   <li>是 Map → 强转后返回</li>
     *   <li>不是 Map（如 JSON 数组）→ 抛出 SQLException</li>
     * </ol>
     *
     * @param value 数据库返回的 JSON 字符串
     * @return 解析后的 Map，输入为 null 或空白时返回 null
     * @throws SQLException 解析失败或 JSON 根节点不是对象时抛出
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String value) throws SQLException {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            Object parsed = OBJECT_MAPPER.readValue(value, Object.class);
            if (parsed instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
            throw new SQLException("JSONB value is not an object");
        } catch (JsonProcessingException e) {
            throw new SQLException("Failed to parse JSONB value", e);
        }
    }
}
