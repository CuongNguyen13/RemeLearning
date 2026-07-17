package com.remelearning.dashboard.mapper;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

/**
 * Persists a {@code List<String>} as a JSON array in a single TEXT column (e.g.
 * {@code recent_recommendations.exercises}). Duplicated from recommendation-service's copy of the
 * same class rather than shared via common, since common deliberately carries no MyBatis
 * dependency (see this repo's CLAUDE.md) - the same reasoning behind each service's own
 * Boot4CompatConfig.
 */
@MappedTypes(List.class)
public class StringListJsonTypeHandler extends BaseTypeHandler<List<String>> {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
	};

	// Writes the list as a JSON array string into the (non-null) column parameter.
	@Override
	public void setNonNullParameter(PreparedStatement ps, int columnIndex, List<String> parameter, JdbcType jdbcType)
			throws SQLException {
		ps.setString(columnIndex, toJson(parameter));
	}

	// Reads a JSON array string back into a list, looked up by column name.
	@Override
	public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
		return fromJson(rs.getString(columnName));
	}

	// Reads a JSON array string back into a list, looked up by column index.
	@Override
	public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
		return fromJson(rs.getString(columnIndex));
	}

	// Reads a JSON array string back into a list from a stored procedure's output parameter.
	@Override
	public List<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
		return fromJson(cs.getString(columnIndex));
	}

	// Serializes the list to a JSON array string; wraps any Jackson failure as SQLException, the
	// checked exception type MyBatis type handlers are expected to throw.
	private static String toJson(List<String> value) throws SQLException {
		try {
			return MAPPER.writeValueAsString(value);
		} catch (Exception ex) {
			throw new SQLException("Failed to serialize exercises to JSON", ex);
		}
	}

	// Deserializes a JSON array string back into a list; null column value maps to a null list.
	private static List<String> fromJson(String json) throws SQLException {
		if (json == null) {
			return null;
		}
		try {
			return MAPPER.readValue(json, STRING_LIST);
		} catch (Exception ex) {
			throw new SQLException("Failed to deserialize exercises JSON: " + json, ex);
		}
	}
}
