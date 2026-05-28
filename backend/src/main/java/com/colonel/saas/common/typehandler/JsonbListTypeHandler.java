package com.colonel.saas.common.typehandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
import java.util.List;

/**
 * MyBatis JSONB 字符串列表类型处理器。
 *
 * <p>处理 PostgreSQL jsonb 列与 Java {@code List<String>} 的映射，
 * 使用 Jackson {@link ObjectMapper} 进行 JSON 数组序列化和反序列化。</p>
 *
 * <h3>使用场景</h3>
 * <p>当实体字段类型为 {@code List<String>}，且数据库列类型为 jsonb 时使用。
 * 典型场景包括：标签列表、图片 URL 列表、角色列表等需要存储 JSON 数组的字段。</p>
 *
 * <h3>与其他 JSONB 处理器的区别</h3>
 * <ul>
 *   <li>{@link com.colonel.saas.common.handler.JsonbTypeHandler}（handler 包）：String ↔ jsonb</li>
 *   <li>{@link JsonbTypeHandler}（typehandler 包）：Map&lt;String, Object&gt; ↔ jsonb</li>
 *   <li><b>本类</b>：List&lt;String&gt; ↔ jsonb，适用于 JSON 数组场景</li>
 * </ul>
 *
 * <h3>错误处理</h3>
 * <ul>
 *   <li>序列化失败：抛出 {@link SQLException}，包含原始 {@link JsonProcessingException}</li>
 *   <li>反序列化失败：抛出 {@link SQLException}，包含原始异常</li>
 *   <li>数据库值为 null 或空白字符串：返回 null</li>
 * </ul>
 *
 * @see JsonbTypeHandler Map 版本的 JSONB 处理器
 * @see com.colonel.saas.common.handler.JsonbTypeHandler 字符串版本的 JSONB 处理器
 */
@MappedTypes({List.class})
@MappedJdbcTypes(JdbcType.OTHER)
public class JsonbListTypeHandler extends BaseTypeHandler<List<String>> {

    /** Jackson JSON 序列化/反序列化器（线程安全，全局共享） */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 类型引用，用于 Jackson 泛型反序列化 List&lt;String&gt; */
    private static final TypeReference<List<String>> TYPE = new TypeReference<>() {};

    /**
     * 设置非空 JSONB 参数（Java List → SQL jsonb）。
     *
     * <p>将 List 序列化为 JSON 数组字符串后，通过 {@code Types.OTHER} 写入 PostgreSQL 的 jsonb 列。</p>
     *
     * @param ps        PreparedStatement 对象
     * @param i         参数索引（从 1 开始）
     * @param parameter 待写入的字符串列表
     * @param jdbcType  JDBC 类型
     * @throws SQLException 序列化失败时抛出
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<String> parameter, JdbcType jdbcType)
            throws SQLException {
        try {
            ps.setObject(i, OBJECT_MAPPER.writeValueAsString(parameter), Types.OTHER);
        } catch (JsonProcessingException e) {
            throw new SQLException("Failed to serialize JSONB list parameter", e);
        }
    }

    /**
     * 根据列名从 ResultSet 获取 JSONB 并解析为字符串列表。
     *
     * @param rs         ResultSet 对象
     * @param columnName 列名
     * @return 解析后的字符串列表，数据库为 NULL 或空字符串时返回 null
     * @throws SQLException 解析失败时抛出
     */
    @Override
    public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    /**
     * 根据列索引从 ResultSet 获取 JSONB 并解析为字符串列表。
     *
     * @param rs          ResultSet 对象
     * @param columnIndex 列索引
     * @return 解析后的字符串列表，数据库为 NULL 或空字符串时返回 null
     * @throws SQLException 解析失败时抛出
     */
    @Override
    public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    /**
     * 从 CallableStatement 获取 JSONB 并解析为字符串列表。
     *
     * @param cs          CallableStatement 对象
     * @param columnIndex 列索引
     * @return 解析后的字符串列表，数据库为 NULL 或空字符串时返回 null
     * @throws SQLException 解析失败时抛出
     */
    @Override
    public List<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    /**
     * 将 JSON 数组字符串解析为 {@code List&lt;String&gt;}。
     *
     * <p>使用 {@link TypeReference} 保留泛型信息，确保 Jackson 正确反序列化为字符串列表。
     * null 或空白字符串安全返回 null。</p>
     *
     * @param value 数据库返回的 JSON 数组字符串
     * @return 解析后的字符串列表，输入为 null 或空白时返回 null
     * @throws SQLException JSON 解析失败时抛出
     */
    private List<String> parse(String value) throws SQLException {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(value, TYPE);
        } catch (JsonProcessingException e) {
            throw new SQLException("Failed to parse JSONB list value", e);
        }
    }
}
