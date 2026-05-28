package com.colonel.saas.common.handler;

import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.apache.ibatis.type.TypeHandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * MyBatis UUID 类型处理器，实现 PostgreSQL uuid 类型与 Java {@link UUID} 的双向映射。
 *
 * <p>通过 {@code @MappedTypes(UUID.class)} 和 {@code @MappedJdbcTypes(JdbcType.OTHER)}
 * 声明映射关系。注册到 MyBatis-Plus 的 {@code type-handlers-package} 配置后全局生效，
 * 无需在每个 Mapper XML 中单独指定 typeHandler。</p>
 *
 * <h3>工作原理</h3>
 * <ul>
 *   <li><b>写入</b>（Java → 数据库）：通过 {@code setObject} 直接传递 UUID 对象，
 *       JDBC 驱动自动将其转换为 PostgreSQL 的 uuid 类型</li>
 *   <li><b>读取</b>（数据库 → Java）：从 ResultSet 读取 Object 后，
 *       如果已经是 UUID 类型则直接返回，否则通过 {@code UUID.fromString()} 解析字符串</li>
 * </ul>
 *
 * <h3>使用场景</h3>
 * <p>所有继承 {@link com.colonel.saas.common.base.BaseEntity} 的实体，
 * 其 {@code id}、{@code createBy}、{@code updateBy} 字段均通过此处理器进行 UUID 映射。</p>
 *
 * @see com.colonel.saas.common.base.BaseEntity 使用此处理器的实体基类
 */
@MappedTypes(UUID.class)
@MappedJdbcTypes(value = JdbcType.OTHER)
public class UUIDTypeHandler implements TypeHandler<UUID> {

    /**
     * 设置 PreparedStatement 参数（Java UUID → SQL 参数）。
     *
     * @param ps        PreparedStatement 对象
     * @param i         参数索引（从 1 开始）
     * @param parameter Java UUID 值，可为 null
     * @param jdbcType  JDBC 类型（实际未使用，由驱动自动判断）
     * @throws SQLException SQL 异常
     */
    @Override
    public void setParameter(PreparedStatement ps, int i, UUID parameter, JdbcType jdbcType) throws SQLException {
        // 直接传递 UUID 对象，JDBC 驱动负责类型转换
        ps.setObject(i, parameter);
    }

    /**
     * 根据列名从 ResultSet 获取 UUID 值。
     *
     * @param rs         ResultSet 对象
     * @param columnName 列名
     * @return UUID 值，数据库为 NULL 时返回 null
     * @throws SQLException SQL 异常
     */
    @Override
    public UUID getResult(ResultSet rs, String columnName) throws SQLException {
        Object value = rs.getObject(columnName);
        return toUUID(value);
    }

    /**
     * 根据列索引从 ResultSet 获取 UUID 值。
     *
     * @param rs          ResultSet 对象
     * @param columnIndex 列索引（从 1 开始）
     * @return UUID 值，数据库为 NULL 时返回 null
     * @throws SQLException SQL 异常
     */
    @Override
    public UUID getResult(ResultSet rs, int columnIndex) throws SQLException {
        Object value = rs.getObject(columnIndex);
        return toUUID(value);
    }

    /**
     * 从 CallableStatement 获取 UUID 值（存储过程返回值）。
     *
     * @param cs          CallableStatement 对象
     * @param columnIndex 列索引（从 1 开始）
     * @return UUID 值，数据库为 NULL 时返回 null
     * @throws SQLException SQL 异常
     */
    @Override
    public UUID getResult(CallableStatement cs, int columnIndex) throws SQLException {
        Object value = cs.getObject(columnIndex);
        return toUUID(value);
    }

    /**
     * 将数据库返回的 Object 转换为 Java UUID。
     *
     * <p>处理两种情况：</p>
     * <ul>
     *   <li>JDBC 驱动直接返回 UUID 对象（如 PostgreSQL JDBC 42.x+）→ 直接强转</li>
     *   <li>JDBC 驱动返回字符串表示 → 通过 {@code UUID.fromString()} 解析</li>
     * </ul>
     *
     * @param value 数据库返回的原始对象
     * @return 转换后的 UUID，输入为 null 时返回 null
     */
    private UUID toUUID(Object value) {
        if (value == null) {
            return null;
        }
        // 优先尝试直接强转（PostgreSQL JDBC 驱动通常直接返回 UUID）
        if (value instanceof UUID u) {
            return u;
        }
        // 回退到字符串解析（兼容其他 JDBC 驱动或 CAST 场景）
        return UUID.fromString(value.toString());
    }
}
