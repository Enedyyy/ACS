package com.acs.finance.dao;

import com.acs.finance.db.Db;

import java.sql.*;
import java.util.UUID;

public class UserDao {
	public String insert(String username, String passwordHash) throws SQLException {
		String sql = "INSERT INTO users (username, password_hash) VALUES (?, ?) RETURNING id";
		try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
			ps.setString(1, username);
			ps.setString(2, passwordHash);
			try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getObject(1).toString(); }
		}
	}

	public Record findByUsername(String username) throws SQLException {
		String sql = "SELECT id, username, password_hash, group_id, share FROM users WHERE username=?";
		try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
			ps.setString(1, username);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) return null;
				Record r = new Record();
				r.id = rs.getObject("id").toString();
				r.username = rs.getString("username");
				r.passwordHash = rs.getString("password_hash");
				Object gid = rs.getObject("group_id");
				r.groupId = gid == null ? null : gid.toString();
				r.share = rs.getBigDecimal("share") == null ? 1.0 : rs.getBigDecimal("share").doubleValue();
				return r;
			}
		}
	}

	public static class Record {
		public String id; public String username; public String passwordHash; public String groupId; public double share;
	}
}



