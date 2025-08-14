package com.mlab.askvistax.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mlab.askvistax.pojo.AudioAnalyze;
import com.mlab.askvistax.pojo.Dimensions;
import com.mlab.askvistax.pojo.VideoAnalyze;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.*;

@MappedTypes({Dimensions.class, AudioAnalyze.class, VideoAnalyze.class})
public class JsonTypeHandler<T> extends BaseTypeHandler<T> {

    private static final ObjectMapper mapper = new ObjectMapper();
    private final Class<T> type;

    public JsonTypeHandler(Class<T> type) {
        if (type == null) throw new IllegalArgumentException("Type argument cannot be null");
        this.type = type;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, toJson(parameter));
    }

    @Override
    public T getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toObject(rs.getString(columnName));
    }

    @Override
    public T getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toObject(rs.getString(columnIndex));
    }

    @Override
    public T getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toObject(cs.getString(columnIndex));
    }

    private String toJson(T obj) throws SQLException {
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new SQLException("Failed to convert object to JSON", e);
        }
    }

    private T toObject(String json) throws SQLException {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return mapper.readValue(json, type);
        } catch (Exception e) {
            throw new SQLException("Failed to convert JSON to object", e);
        }
    }
}

