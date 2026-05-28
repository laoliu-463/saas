package com.colonel.saas.common.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * MyBatis JSONB 字符串类型处理器（handler 包版本）。
 *
 * <p>处理 PostgreSQL jsonb 列与 Java {@link String} 的映射。
 * 与 {@link com.colonel.saas.common.typehandler.JsonbTypeHandler}（Map 版本）不同，
 * 此处理器将 JSONB 内容作为原始 JSON 字符串读写，不做结构化解析。</p>
 *
 * <h3>使用场景</h3>
 * <p>当实体字段类型为 String，但数据库列类型为 jsonb 时使用。
 * 例如存储扩展信息（extra_info）等不需要在 Java 层按 key 访问的场景。</p>
 *
 * <h3>与 typehandler 包版本的区别</h3>
 * <ul>
 *   <li><b>handler 包（本类）</b>：String ↔ jsonb，适用于原始 JSON 字符串存储</li>
 *   <li><b>typehandler 包</b>：Map&lt;String, Object&gt; ↔ jsonb，适用于结构化 JSON 对象</li>
 * </ul>
 *
 * @see com.colonel.saas.common.typehandler.JsonbTypeHandler Map 版本的 JSONB 处理器
 * @see com.colonel.saas.common.typehandler.JsonbListTypeHandler List 版本的 JSONB 处理器
 */
@MappedTypes(String.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class JsonbTypeHandler extends BaseTypeHandler<String> {

    /**
     * 设置非空 JSONB 参数（Java String → SQL jsonb）。
     *
     * <p>使用 {@code Types.OTHER} 类型标记，让 PostgreSQL JDBC 驱动将字符串
     * 作为 jsonb 类型写入，而非普通的 varchar。</p>
     *
     * @param ps        PreparedStatement 对象
     * @param i         参数索引（从 1 开始）
     * @param parameter JSON 字符串
     * @param jdbcType  JDBC 类型
     * @throws SQLException SQL 异常
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setObject(i, parameter, Types.OTHER);
    }

    /**
     * 根据列名从 ResultSet 获取 JSONB 字符串值。
     *
     * @param rs         ResultSet 对象
     * @param columnName 列名
     * @return JSON 字符串，数据库为 NULL 时返回 null
     * @throws SQLException SQL 异常
     */
    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getString(columnName);
    }

    /**
     * 根据列索引从 ResultSet 获取 JSONB 字符串值。
     *
     * @param rs          ResultSet 对象
     * @param columnIndex 列索引
     * @return JSON 字符串，数据库为 NULL 时返回 null
     * @throws SQLException SQL 异常
     */
    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getString(columnIndex);
    }

    /**
     * 从 CallableStatement 获取 JSONB 字符串值。
     *
     * @param cs          CallableStatement 对象
     * @param columnIndex 列索引
     * @return JSON 字符串，数据库为 NULL 时返回 null
     * @throws SQLException SQL 异常
     */
    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return cs.getString(columnIndex);
    }
}
