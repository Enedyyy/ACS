package com.acs.finance.dao;

import com.acs.finance.db.Db;

import java.sql.*;

public class SessionDao {
	public void create(String sid, String username) throws SQLException {
		String sql = "INSERT INTO sessions (sid, username) VALUES (?, ?) ON CONFLICT (sid) DO UPDATE SET username=EXCLUDED.username, created_at=now()";
		try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
			ps.setString(1, sid);
			ps.setString(2, username);
			ps.executeUpdate();
		}
	}

	public void delete(String sid) throws SQLException {
		try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement("DELETE FROM sessions WHERE sid=?")) {
			ps.setString(1, sid);
			ps.executeUpdate();
		}
	}

	public String getUsernameBySid(String sid) throws SQLException {
		try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement("SELECT username FROM sessions WHERE sid=?")) {
			ps.setString(1, sid);
			try (ResultSet rs = ps.executeQuery()) { return rs.next()? rs.getString(1): null; }
		}
	}
}



