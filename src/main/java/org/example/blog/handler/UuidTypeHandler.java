package org.example.blog.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * MyBatis UUID 类型处理器
 * 让 MyBatis 能正确处理 java.util.UUID 与 PostgreSQL UUID 之间的转换
 */
@MappedTypes(UUID.class)
public class UuidTypeHandler extends BaseTypeHandler<UUID> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, UUID parameter, JdbcType jdbcType) throws SQLException {
        ps.setObject(i, parameter);
    }

    @Override
    public UUID getNullableResult(ResultSet rs, String columnName) throws SQLException {
        Object value = rs.getObject(columnName);
        return toUUID(value);
    }

    @Override
    public UUID getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        Object value = rs.getObject(columnIndex);
        return toUUID(value);
    }

    @Override
    public UUID getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        Object value = cs.getObject(columnIndex);
        return toUUID(value);
    }

    private UUID toUUID(Object value) {
        if (value == null) return null;
        if (value instanceof UUID) return (UUID) value;
        return UUID.fromString(value.toString());
    }
}
