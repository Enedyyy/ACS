package com.acs.finance.service;

import com.acs.finance.db.Db;
import com.acs.finance.dao.GroupDao;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GroupService {
	public static class Group {
		public final String id;
		public final String name;
		public final Map<String, Double> memberShare = new ConcurrentHashMap<>(); // userId -> share 0..1
		public Group(String id, String name) { this.id = id; this.name = name; }
	}

	private final Map<String, Group> groups = new ConcurrentHashMap<>();
	private final Map<String, String> userToGroup = new ConcurrentHashMap<>();
	private final boolean useDb = Db.isConfigured();
	private final GroupDao dao = new GroupDao();

	public Group create(String name) {
		String id = UUID.randomUUID().toString();
		if (useDb) { try { id = dao.create(name==null?"Группа":name); } catch (Exception ignored) {} }
		Group g = new Group(id, name == null ? "Группа" : name);
		groups.put(id, g);
		return g;
	}

	public Group join(String userId, String groupId, double share) {
		Group g = groups.get(groupId);
		if (g == null) g = new Group(groupId, "Группа");
		g.memberShare.put(userId, Math.max(0.0, Math.min(1.0, share)));
		userToGroup.put(userId, groupId);
		if (useDb) { try { dao.join(userId, groupId, share); } catch (Exception ignored) {} }
		groups.putIfAbsent(groupId, g);
		return g;
	}

	public String userGroupId(String userId) {
		String gid = userToGroup.get(userId);
		if (gid == null && useDb) { try { gid = dao.userGroupId(userId); } catch (Exception ignored) {} }
		return gid;
	}
	public Group get(String id) { return groups.get(id); }
	public Map<String, Double> members(String id) {
		Group g = groups.get(id);
		if (g != null) return new HashMap<>(g.memberShare);
		if (useDb) { try { return dao.members(id); } catch (Exception ignored) {} }
		return Map.of();
	}

	public void leave(String userId) {
		String gid = userToGroup.remove(userId);
		if (gid != null) {
			Group g = groups.get(gid);
			if (g != null) g.memberShare.remove(userId);
		}
		if (useDb) { try { dao.leave(userId); } catch (Exception ignored) {} }
	}

	public Double myShare(String userId) {
		String gid = userGroupId(userId);
		if (gid == null) return null;
		Map<String, Double> m = members(gid);
		return m.get(userId);
	}
}


