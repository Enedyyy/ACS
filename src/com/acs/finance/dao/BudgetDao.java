package com.acs.finance.dao;

import com.acs.finance.db.Db;

import java.sql.*;
import java.util.*;

public class BudgetDao {
	public void upsertLimit(String userId, String category, double limit) throws SQLException {
		String sql = "INSERT INTO budgets (user_id, category, limit_amount, spent) VALUES (?,?,?,0) " +
				"ON CONFLICT (user_id, category) DO UPDATE SET limit_amount=EXCLUDED.limit_amount";
		try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
			ps.setObject(1, java.util.UUID.fromString(userId));
			ps.setString(2, category);
			ps.setBigDecimal(3, java.math.BigDecimal.valueOf(limit));
			ps.executeUpdate();
		}
	}

	public void addSpent(String userId, String category, double amount) throws SQLException {
		String sql = "UPDATE budgets SET spent = spent + ? WHERE user_id=? AND category=?";
		try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
			ps.setBigDecimal(1, java.math.BigDecimal.valueOf(amount));
			ps.setObject(2, java.util.UUID.fromString(userId));
			ps.setString(3, category);
			ps.executeUpdate();
		}
	}

	public List<Map<String,Object>> list(String userId) throws SQLException {
		String sql = "SELECT category, limit_amount, spent FROM budgets WHERE user_id=? ORDER BY category";
		try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
			ps.setObject(1, java.util.UUID.fromString(userId));
			List<Map<String,Object>> out = new ArrayList<>();
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					Map<String,Object> m = new LinkedHashMap<>();
					m.put("category", rs.getString("category"));
					m.put("limit", rs.getBigDecimal("limit_amount").doubleValue());
					m.put("spent", rs.getBigDecimal("spent").doubleValue());
					out.add(m);
				}
			}
			return out;
		}
	}

	public void delete(String userId, String category) throws SQLException {
		String sql = "DELETE FROM budgets WHERE user_id=? AND category=?";
		try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
			ps.setObject(1, java.util.UUID.fromString(userId));
			ps.setString(2, category);
			ps.executeUpdate();
		}
	}
}



