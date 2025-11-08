package com.acs.finance.dao;

import com.acs.finance.db.Db;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;

public class TransactionDao {
	public String insert(String userId, LocalDate date, String category, String description, double amount) throws SQLException {
		String sql = "INSERT INTO transactions (user_id, date_epoch_day, category, description, amount) VALUES (?,?,?,?,?) RETURNING id";
		try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
			ps.setObject(1, java.util.UUID.fromString(userId));
			ps.setLong(2, date.toEpochDay());
			ps.setString(3, category);
			ps.setString(4, description);
			ps.setBigDecimal(5, java.math.BigDecimal.valueOf(amount));
			try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getObject(1).toString(); }
		}
	}

	public List<Map<String,Object>> list(String userId, LocalDate from, LocalDate to, String category) throws SQLException {
		StringBuilder sb = new StringBuilder("SELECT id, date_epoch_day, category, description, amount FROM transactions WHERE user_id=?");
		List<Object> params = new ArrayList<>(); params.add(java.util.UUID.fromString(userId));
		if (from != null) { sb.append(" AND date_epoch_day>=?"); params.add(from.toEpochDay()); }
		if (to != null) { sb.append(" AND date_epoch_day<=?"); params.add(to.toEpochDay()); }
		if (category != null && !category.isBlank()) { sb.append(" AND lower(category)=lower(?)"); params.add(category); }
		sb.append(" ORDER BY date_epoch_day ASC");
		try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sb.toString())) {
			for (int i=0;i<params.size();i++) ps.setObject(i+1, params.get(i));
			List<Map<String,Object>> out = new ArrayList<>();
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					Map<String,Object> m = new LinkedHashMap<>();
					m.put("id", rs.getObject("id").toString());
					m.put("dateEpochDay", rs.getLong("date_epoch_day"));
					m.put("category", rs.getString("category"));
					m.put("description", rs.getString("description"));
					m.put("amount", rs.getBigDecimal("amount").doubleValue());
					out.add(m);
				}
			}
			return out;
		}
	}

	public void delete(String id, String userId) throws SQLException {
		String sql = "DELETE FROM transactions WHERE id=? AND user_id=?";
		try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
			ps.setObject(1, java.util.UUID.fromString(id));
			ps.setObject(2, java.util.UUID.fromString(userId));
			ps.executeUpdate();
		}
	}
}



