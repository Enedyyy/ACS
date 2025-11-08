package com.acs.finance.dao;

import com.acs.finance.db.Db;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;

public class ReminderDao {
	public String insert(String userId, LocalDate due, String message, Double amount) throws SQLException {
		String sql = "INSERT INTO reminders (user_id, due_epoch_day, message, amount, sent) VALUES (?,?,?,?,FALSE) RETURNING id";
		try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
			ps.setObject(1, java.util.UUID.fromString(userId));
			ps.setLong(2, due.toEpochDay());
			ps.setString(3, message);
			if (amount == null) ps.setNull(4, Types.NUMERIC); else ps.setBigDecimal(4, java.math.BigDecimal.valueOf(amount));
			try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getObject(1).toString(); }
		}
	}

	public List<Map<String,Object>> list(String userId) throws SQLException {
		String sql = "SELECT id, due_epoch_day, message, amount, sent FROM reminders WHERE user_id=? ORDER BY due_epoch_day";
		try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
			ps.setObject(1, java.util.UUID.fromString(userId));
			List<Map<String,Object>> out = new ArrayList<>();
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					Map<String,Object> m = new LinkedHashMap<>();
					m.put("id", rs.getObject("id").toString());
					m.put("dueEpochDay", rs.getLong("due_epoch_day"));
					m.put("message", rs.getString("message"));
					var amt = rs.getBigDecimal("amount");
					m.put("amount", amt == null ? null : amt.doubleValue());
					m.put("sent", rs.getBoolean("sent"));
					out.add(m);
				}
			}
			return out;
		}
	}

	public List<Map<String,Object>> dueAndMarkSent(String userId, long todayEpochDay) throws SQLException {
		String select = "SELECT id, message, amount FROM reminders WHERE user_id=? AND sent=FALSE AND due_epoch_day<=?";
		String update = "UPDATE reminders SET sent=TRUE WHERE id=?";
		try (Connection c = Db.get()) {
			c.setAutoCommit(false);
			List<Map<String,Object>> out = new ArrayList<>();
			try (PreparedStatement ps = c.prepareStatement(select)) {
				ps.setObject(1, java.util.UUID.fromString(userId));
				ps.setLong(2, todayEpochDay);
				try (ResultSet rs = ps.executeQuery()) {
					while (rs.next()) {
						Map<String,Object> m = new LinkedHashMap<>();
						m.put("id", rs.getObject("id").toString());
						m.put("message", rs.getString("message"));
						var amt = rs.getBigDecimal("amount");
						m.put("amount", amt == null ? null : amt.doubleValue());
						out.add(m);
					}
				}
			}
			try (PreparedStatement up = c.prepareStatement(update)) {
				for (var m : out) { up.setObject(1, java.util.UUID.fromString((String)m.get("id"))); up.addBatch(); }
				up.executeBatch();
			}
			c.commit();
			return out;
		}
	}
}



