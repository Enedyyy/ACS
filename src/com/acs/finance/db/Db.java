package com.acs.finance.db;

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
		return DriverManager.getConnection(url, user, pass);
	}

	public static boolean testConnection() {
		if (!isConfigured()) return false;
		try (Connection c = get()) { return c != null && !c.isClosed(); } catch (Exception e) { return false; }
	}

	private static String getenv(String k) {
		String v = System.getenv(k);
		if (v == null || v.isBlank()) return System.getProperty(k);
		return v;
	}
}



