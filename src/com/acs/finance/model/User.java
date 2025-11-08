package com.acs.finance.model;

import java.util.Objects;

public class User {
	public final String id;
	public final String username;
	public final String passwordHash;
	public final String groupId; // optional
	public final double share; // 0..1

	public User(String id, String username, String passwordHash, String groupId, double share) {
		this.id = id;
		this.username = username;
		this.passwordHash = passwordHash;
		this.groupId = groupId;
		this.share = share;
	}

	@Override public boolean equals(Object o) { if (this == o) return true; if (o == null || getClass() != o.getClass()) return false; User user = (User) o; return Objects.equals(id, user.id); }
	@Override public int hashCode() { return Objects.hash(id); }
}


