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
 * MyBatis TypeHandler for PostgreSQL UUID ↔ Java UUID.
 * 注册到 mybatis-plus.configuration.type-handlers-package 后全局生效。
 */
@MappedTypes(UUID.class)
@MappedJdbcTypes(value = JdbcType.OTHER)
public class UUIDTypeHandler implements TypeHandler<UUID> {

    @Override
    public void setParameter(PreparedStatement ps, int i, UUID parameter, JdbcType jdbcType) throws SQLException {
        ps.setObject(i, parameter);
    }

    @Override
    public UUID getResult(ResultSet rs, String columnName) throws SQLException {
        Object value = rs.getObject(columnName);
        return toUUID(value);
    }

    @Override
    public UUID getResult(ResultSet rs, int columnIndex) throws SQLException {
        Object value = rs.getObject(columnIndex);
        return toUUID(value);
    }

    @Override
    public UUID getResult(CallableStatement cs, int columnIndex) throws SQLException {
        Object value = cs.getObject(columnIndex);
        return toUUID(value);
    }

    private UUID toUUID(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof UUID u) {
            return u;
        }
        return UUID.fromString(value.toString());
    }
}
