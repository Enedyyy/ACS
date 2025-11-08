package com.acs.finance.dao;

import com.acs.finance.db.Db;

import java.sql.*;

public class GoalDao {
	public void upsert(String userId, String name, double target, double current) throws SQLException {
		String sql = "INSERT INTO goals (user_id, name, target_amount, current_amount) VALUES (?,?,?,?) " +
				"ON CONFLICT (user_id) DO UPDATE SET name=EXCLUDED.name, target_amount=EXCLUDED.target_amount, current_amount=EXCLUDED.current_amount";
		try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
			ps.setObject(1, java.util.UUID.fromString(userId));
			ps.setString(2, name);
			ps.setBigDecimal(3, java.math.BigDecimal.valueOf(target));
			ps.setBigDecimal(4, java.math.BigDecimal.valueOf(current));
			ps.executeUpdate();
		}
	}
}



