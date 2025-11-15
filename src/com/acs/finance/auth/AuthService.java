package com.acs.finance.auth;

import com.acs.finance.db.Db;
import com.acs.finance.dao.SessionDao;
import com.acs.finance.dao.UserDao;
import com.acs.finance.model.User;
import com.acs.finance.util.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AuthService {
	private final Map<String, User> usersByName = new ConcurrentHashMap<>();
	private final Map<String, String> sessions = new ConcurrentHashMap<>(); // sid -> username
	private final boolean useDb = Db.isConfigured();
	private final UserDao userDao = new UserDao();
	private final SessionDao sessionDao = new SessionDao();

	public User register(String username, String password) {
		if (username == null || username.isBlank() || password == null || password.isBlank()) {
			Logger.warning("Registration attempt with empty username or password");
			return null;
		}
		if (usersByName.containsKey(username)) {
			Logger.warning("Registration failed: username already exists: %s", username);
			return null;
		}
		String id;
		String hash = hashPassword(password);
		if (useDb) {
			try {
				id = userDao.insert(username, hash);
				Logger.debug("User registered in database: %s (id: %s)", username, id);
			} catch (Exception e) {
				Logger.error("Failed to register user in database: %s", e, username);
				return null;
			}
		} else {
			id = UUID.randomUUID().toString();
			Logger.debug("User registered in memory: %s (id: %s)", username, id);
		}
		User u = new User(id, username, hash, null, 1.0);
		usersByName.put(username, u);
		Logger.info("User successfully registered: %s", username);
		return u;
	}

	public User login(String username, String password) {
		User u = usersByName.get(username);
		if (u == null && useDb) {
			try {
				var r = userDao.findByUsername(username);
				if (r != null) {
					u = new User(r.id, r.username, r.passwordHash, r.groupId, r.share);
					usersByName.put(u.username, u);
					Logger.debug("User loaded from database: %s", username);
				}
			} catch (Exception e) {
				Logger.error("Failed to load user from database: %s", e, username);
			}
		}
		if (u == null) {
			Logger.warning("Login failed: user not found: %s", username);
			return null;
		}
		if (!verifyPassword(password, u.passwordHash)) {
			Logger.warning("Login failed: invalid password for user: %s", username);
			return null;
		}
		Logger.debug("User authenticated: %s", username);
		return u;
	}

	public String createSession(String username) {
		String sid = genToken();
		sessions.put(sid, username);
		if (useDb) {
			try {
				sessionDao.create(sid, username);
				Logger.debug("Session created in database: %s", username);
			} catch (Exception e) {
				Logger.error("Failed to create session in database: %s", e, username);
			}
		}
		Logger.debug("Session created: user=%s, sid=%s...", username, sid.substring(0, Math.min(8, sid.length())));
		return sid;
	}

	public void destroySession(String sid) {
		if (sid != null) {
			String username = sessions.get(sid);
			sessions.remove(sid);
			if (useDb) {
				try {
					sessionDao.delete(sid);
					Logger.debug("Session deleted from database: %s", sid.substring(0, Math.min(8, sid.length())) + "...");
				} catch (Exception e) {
					Logger.error("Failed to delete session from database", e);
				}
			}
			if (username != null) {
				Logger.debug("Session destroyed: user=%s", username);
			}
		}
	}

	public User getUserBySession(String sid) {
		String username = sessions.get(sid);
		if (username == null && useDb) {
			try { username = sessionDao.getUsernameBySid(sid); } catch (Exception ignored) {}
		}
		if (username == null) return null;
		return usersByName.get(username);
	}

	private static String hashPassword(String pwd) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] dig = md.digest(pwd.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(dig);
		} catch (Exception e) { throw new RuntimeException(e); }
	}

	private static boolean verifyPassword(String pwd, String hash) {
		return hashPassword(pwd).equals(hash);
	}

	private static String genToken() {
		byte[] b = new byte[24];
		new SecureRandom().nextBytes(b);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
	}
}


