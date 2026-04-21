package com.colonel.saas.common.handler;

import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UUIDTypeHandlerTest {

    private final UUIDTypeHandler handler = new UUIDTypeHandler();

    @Test
    void setParameter_shouldBindUuidAsJdbcObject() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        UUID value = UUID.randomUUID();

        handler.setParameter(ps, 1, value, JdbcType.OTHER);

        verify(ps).setObject(1, value);
    }

    @Test
    void getResult_shouldParseStringUuidFromResultSet() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        UUID expected = UUID.randomUUID();
        when(rs.getObject("role_id")).thenReturn(expected.toString());

        UUID actual = handler.getResult(rs, "role_id");

        assertThat(actual).isEqualTo(expected);
    }
}
