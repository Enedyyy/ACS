package com.acs.finance.db;

import com.acs.finance.util.Logger;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Db {
    static {
        try { Class.forName("org.postgresql.Driver"); } catch (Throwable ignored) {}
    }
	public static boolean isConfigured() {
		return getenv("DB_URL") != null && !getenv("DB_URL").isBlank();
	}

	public static Connection get() throws SQLException {
		String url = getenv("DB_URL");
		String user = getenv("DB_USER");
		String pass = getenv("DB_PASS");
		Logger.debug("Opening database connection: url=%s, user=%s", url, user);
		try {
			Connection conn = DriverManager.getConnection(url, user, pass);
			Logger.debug("Database connection established");
			return conn;
		} catch (SQLException e) {
			Logger.error("Failed to establish database connection", e);
			throw e;
		}
	}

	public static boolean testConnection() {
		if (!isConfigured()) {
			Logger.debug("Database not configured, skipping connection test");
			return false;
		}
		try (Connection c = get()) {
			boolean ok = c != null && !c.isClosed();
			if (ok) {
				Logger.info("Database connection test: OK");
			} else {
				Logger.warning("Database connection test: connection is closed");
			}
			return ok;
		} catch (Exception e) {
			Logger.error("Database connection test failed", e);
			return false;
		}
	}

	private static String getenv(String k) {
		String v = System.getenv(k);
		if (v == null || v.isBlank()) return System.getProperty(k);
		return v;
	}
}



