package com.acs.finance.service;

import com.acs.finance.db.Db;
import com.acs.finance.dao.BudgetDao;
import com.acs.finance.dao.GoalDao;
import com.acs.finance.dao.ReminderDao;
import com.acs.finance.dao.TransactionDao;
import com.acs.finance.model.Budget;
import com.acs.finance.model.Goal;
import com.acs.finance.model.Reminder;
import com.acs.finance.model.Transaction;
import com.acs.finance.util.Logger;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class FinanceService {
	private final Map<String, List<Transaction>> userTx = new ConcurrentHashMap<>();
	private final Map<String, Map<String, Budget>> userBudgets = new ConcurrentHashMap<>(); // user -> category -> budget
	private final Map<String, Goal> userGoals = new ConcurrentHashMap<>();
	private final Map<String, List<Reminder>> userReminders = new ConcurrentHashMap<>();
	private final boolean useDb = Db.isConfigured();
	private final TransactionDao txDao = new TransactionDao();
	private final BudgetDao budgetDao = new BudgetDao();
	private final GoalDao goalDao = new GoalDao();
	private final ReminderDao reminderDao = new ReminderDao();

	public Transaction addTransaction(String userId, LocalDate date, String category, String description, double amount) {
		String id;
		if (useDb) {
			try {
				id = txDao.insert(userId, date, category, description, amount);
				Logger.debug("Transaction saved to database: userId=%s, amount=%.2f", userId, amount);
			} catch (Exception e) {
				Logger.error("Failed to save transaction to database", e);
				id = UUID.randomUUID().toString();
			}
		} else {
			id = UUID.randomUUID().toString();
		}
		Transaction t = new Transaction(id, userId, date.toEpochDay(), category, description, amount);
		userTx.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(t);
		if (category != null && amount < 0) {
			if (useDb) {
				try {
					budgetDao.addSpent(userId, category, -amount);
				} catch (Exception e) {
					Logger.error("Failed to update budget spent in database", e);
				}
			}
			Budget b = userBudgets.computeIfAbsent(userId, k -> new ConcurrentHashMap<>()).computeIfAbsent(category, c -> new Budget(userId, c, 0.0));
			b.spent += -amount;
			Logger.debug("Budget updated: userId=%s, category=%s, spent=%.2f", userId, category, b.spent);
		}
		return t;
	}

	public List<Transaction> listTransactions(String userId, LocalDate from, LocalDate to, String category) {
		if (useDb) {
			try {
				var rows = txDao.list(userId, from, to, category);
				List<Transaction> list = new ArrayList<>();
				for (var m : rows) {
					list.add(new Transaction(
						(String)m.get("id"), userId, (Long)m.get("dateEpochDay"),
						(String)m.get("category"), (String)m.get("description"), (Double)m.get("amount")
					));
				}
				return list;
			} catch (Exception ignored) {}
		}
		List<Transaction> all = userTx.getOrDefault(userId, List.of());
		return all.stream().filter(t -> {
			LocalDate d = LocalDate.ofEpochDay(t.dateEpochDay);
			if (from != null && d.isBefore(from)) return false;
			if (to != null && d.isAfter(to)) return false;
			if (category != null && !category.isBlank()) return category.equalsIgnoreCase(t.category);
			return true;
		}).sorted(Comparator.comparingLong(a -> a.dateEpochDay)).collect(Collectors.toList());
	}

	public boolean deleteTransaction(String userId, String txId) {
		List<Transaction> list = userTx.getOrDefault(userId, new ArrayList<>());
		Transaction removed = null;
		if (!list.isEmpty()) {
			for (Transaction t : list) {
				if (t.id.equals(txId)) { removed = t; break; }
			}
			if (removed != null) { list.remove(removed); }
		}
		// if we didn't find in memory, still try to delete from DB
		if (useDb) {
			try {
				txDao.delete(txId, userId);
				Logger.debug("Transaction deleted from database: txId=%s, userId=%s", txId, userId);
			} catch (Exception e) {
				Logger.error("Failed to delete transaction from database", e);
			}
		}
		// adjust budget only if we know the deleted tx
		if (removed != null && removed.amount < 0 && removed.category != null) {
			Budget b = userBudgets.computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
				.computeIfAbsent(removed.category, c -> new Budget(userId, c, 0.0));
			b.spent = Math.max(0, b.spent - (-removed.amount));
			if (useDb) {
				try {
					budgetDao.addSpent(userId, removed.category, -(-removed.amount));
				} catch (Exception e) {
					Logger.error("Failed to update budget after transaction deletion", e);
				}
			}
			Logger.debug("Budget adjusted after transaction deletion: userId=%s, category=%s", userId, removed.category);
		}
		// if either removed in memory or attempted DB delete, consider it ok
		boolean result = removed != null || useDb;
		if (result) {
			Logger.debug("Transaction deleted: txId=%s, userId=%s", txId, userId);
		} else {
			Logger.warning("Transaction not found for deletion: txId=%s, userId=%s", txId, userId);
		}
		return result;
	}

	public Budget setBudget(String userId, String category, double limit) {
		if (useDb) {
			try { budgetDao.upsertLimit(userId, category, limit); } catch (Exception ignored) {}
		}
		Budget b = userBudgets.computeIfAbsent(userId, k -> new ConcurrentHashMap<>()).computeIfAbsent(category, c -> new Budget(userId, c, limit));
		b.limit = limit;
		return b;
	}

	public List<Budget> getBudgets(String userId) {
		if (useDb) {
			try {
				var rows = budgetDao.list(userId);
				List<Budget> out = new ArrayList<>();
				for (var m : rows) {
					Budget b = new Budget(userId, (String)m.get("category"), ((Double)m.get("limit")));
					b.spent = ((Double)m.get("spent"));
					out.add(b);
				}
				return out;
			} catch (Exception ignored) {}
		}
		return new ArrayList<>(userBudgets.getOrDefault(userId, Map.of()).values());
	}

	public boolean deleteBudget(String userId, String category) {
		Map<String, Budget> map = userBudgets.get(userId);
		if (map != null) map.remove(category);
		if (useDb) { try { budgetDao.delete(userId, category); } catch (Exception ignored) {} }
		return true;
	}

	public Goal setGoal(String userId, String name, double target, double current) {
		Goal g = new Goal(userId, name, target, current);
		userGoals.put(userId, g);
		if (useDb) { try { goalDao.upsert(userId, name, target, current); } catch (Exception ignored) {} }
		return g;
	}

	public Goal getGoal(String userId) { return userGoals.get(userId); }

	public Reminder addReminder(String userId, LocalDate due, String message, Double amount) {
		String id = UUID.randomUUID().toString();
		if (useDb) { try { id = new ReminderDao().insert(userId, due, message, amount); } catch (Exception ignored) {} }
		Reminder r = new Reminder(id, userId, due.toEpochDay(), message, amount);
		userReminders.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(r);
		return r;
	}

	public List<Reminder> getReminders(String userId) {
		if (useDb) {
			try {
				var rows = reminderDao.list(userId);
				List<Reminder> list = new ArrayList<>();
				for (var m : rows) {
					Reminder r = new Reminder((String)m.get("id"), userId, (Long)m.get("dueEpochDay"), (String)m.get("message"), (Double)m.get("amount"));
					list.add(r);
				}
				return list;
			} catch (Exception ignored) {}
		}
		return userReminders.getOrDefault(userId, List.of());
	}

	public List<Reminder> consumeDueReminders(String userId, long todayEpochDay) {
		if (useDb) {
			try {
				var rows = reminderDao.dueAndMarkSent(userId, todayEpochDay);
				List<Reminder> list = new ArrayList<>();
				for (var m : rows) {
					Reminder r = new Reminder((String)m.get("id"), userId, todayEpochDay, (String)m.get("message"), (Double)m.get("amount"));
					list.add(r);
				}
				return list;
			} catch (Exception ignored) {}
		}
		List<Reminder> list = userReminders.getOrDefault(userId, List.of());
		List<Reminder> due = new ArrayList<>();
		for (Reminder r : list) {
			if (!r.sent && r.dueEpochDay <= todayEpochDay) { r.sent = true; due.add(r); }
		}
		return due;
	}

	public List<Map<String, Object>> reportByCategory(String userId, LocalDate from, LocalDate to) {
		Map<String, Double> aggr = new HashMap<>();
		for (Transaction t : listTransactions(userId, from, to, null)) {
			String cat = t.category == null ? "Прочее" : t.category;
			double v = aggr.getOrDefault(cat, 0.0);
			if (t.amount < 0) v += -t.amount; // expenses only
			aggr.put(cat, v);
		}
		List<Map<String, Object>> items = new ArrayList<>();
		aggr.forEach((k,v)->{
			Map<String,Object> m = new LinkedHashMap<>();
			m.put("category", k);
			m.put("total", round2(v));
			items.add(m);
		});
		items.sort(Comparator.comparingDouble(a->-(Double)a.get("total")));
		return items;
	}

	public boolean isAnomalousExpense(String userId, String category, double amount) {
		if (amount >= 0) return false;
		List<Transaction> all = userTx.getOrDefault(userId, List.of());
		int count = 0; double sum = 0.0;
		for (int i = all.size()-1; i>=0 && count<10; i--) {
			Transaction t = all.get(i);
			if (t.amount < 0 && (category==null || (t.category!=null && t.category.equalsIgnoreCase(category)))) {
				count++; sum += -t.amount;
			}
		}
		if (count < 3) return false;
		double avg = sum / count;
		return (-amount) > avg * 2.0; // >200% среднего
	}

	public List<Map<String, Object>> recommendations(String userId) {
		List<Map<String, Object>> out = new ArrayList<>();
		for (Budget b : getBudgets(userId)) {
			if (b.limit > 0 && b.spent > b.limit) {
				out.add(rec("Сократить расходы в категории '"+b.category+"'",  (int)Math.round((b.spent - b.limit)) ));
			}
		}
		// Simple heuristic: top expense category -> suggest 10% cut
		List<Map<String,Object>> rep = reportByCategory(userId, LocalDate.now().withDayOfMonth(1), LocalDate.now());
		if (!rep.isEmpty()) {
			String top = (String) rep.get(0).get("category");
			double amt = (Double) rep.get(0).get("total");
			out.add(rec("Оптимизировать категорию '"+top+"' на 10%", (int)Math.round(amt*0.1)));
		}
		return out;
	}

	private static Map<String,Object> rec(String msg, int potentialSave) {
		Map<String,Object> m = new LinkedHashMap<>();
		m.put("message", msg);
		m.put("potentialSave", potentialSave);
		return m;
	}

	public static double round2(double v) { return Math.round(v*100.0)/100.0; }
}


