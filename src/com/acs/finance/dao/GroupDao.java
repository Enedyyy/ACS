package com.acs.finance.dao;

import com.acs.finance.db.Db;

import java.sql.*;
import java.util.*;

public class GroupDao {
	public String create(String name) throws SQLException {
		String sql = "INSERT INTO groups (name) VALUES (?) RETURNING id";
		try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
			ps.setString(1, name);
			try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getObject(1).toString(); }
		}
	}

	public void join(String userId, String groupId, double share) throws SQLException {
		String ins = "INSERT INTO group_members (group_id, user_id, share) VALUES (?,?,?) ON CONFLICT (group_id,user_id) DO UPDATE SET share=EXCLUDED.share";
		try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(ins)) {
			ps.setObject(1, java.util.UUID.fromString(groupId));
			ps.setObject(2, java.util.UUID.fromString(userId));
			ps.setBigDecimal(3, java.math.BigDecimal.valueOf(share));
			ps.executeUpdate();
		}
	}

	public String userGroupId(String userId) throws SQLException {
		String sql = "SELECT group_id FROM group_members WHERE user_id=? LIMIT 1";
		try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
			ps.setObject(1, java.util.UUID.fromString(userId));
			try (ResultSet rs = ps.executeQuery()) { return rs.next()? rs.getObject(1).toString(): null; }
		}
	}

	public Map<String, Double> members(String groupId) throws SQLException {
		String sql = "SELECT user_id, share FROM group_members WHERE group_id=?";
		try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
			ps.setObject(1, java.util.UUID.fromString(groupId));
			Map<String, Double> map = new LinkedHashMap<>();
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					map.put(rs.getObject(1).toString(), rs.getBigDecimal(2).doubleValue());
				}
			}
			return map;
		}
	}

	public void leave(String userId) throws SQLException {
		String sql = "DELETE FROM group_members WHERE user_id=?";
		try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
			ps.setObject(1, java.util.UUID.fromString(userId));
			ps.executeUpdate();
		}
	}
}



