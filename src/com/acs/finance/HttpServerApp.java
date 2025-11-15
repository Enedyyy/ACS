package com.acs.finance;

import com.acs.finance.auth.AuthService;
import com.acs.finance.model.Budget;
import com.acs.finance.model.CurrencyRates;
import com.acs.finance.model.Goal;
import com.acs.finance.model.Transaction;
import com.acs.finance.db.Db;
import com.acs.finance.service.CurrencyService;
import com.acs.finance.service.FinanceService;
import com.acs.finance.service.AutoCategorizer;
import com.acs.finance.service.GroupService;
import com.acs.finance.service.SseHub;
import com.acs.finance.util.Logger;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HttpServerApp {
	private final int port;
	private HttpServer server;
	private ExecutorService executor;

	// Services
	private final AuthService auth = new AuthService();
	private final FinanceService finance = new FinanceService();
	private final CurrencyService currency = new CurrencyService();
	private final SseHub sse = new SseHub();
    private final AutoCategorizer categorizer = new AutoCategorizer();
    private final GroupService groups = new GroupService();

    private java.util.concurrent.ScheduledExecutorService scheduler;

	public HttpServerApp(int port) {
		this.port = port;
	}

	public void start() throws IOException {
		Logger.info("Initializing HTTP server on port %d", port);
		server = HttpServer.create(new InetSocketAddress(port), 0);
		// Thread pool for concurrent clients
		int poolSize = Math.max(4, Runtime.getRuntime().availableProcessors());
		executor = Executors.newFixedThreadPool(poolSize);
		server.setExecutor(executor);
		Logger.debug("Thread pool size: %d", poolSize);

		// Static files
		server.createContext("/", new StaticHandler("web/public"));

		// Health
		server.createContext("/health", exchange -> {
			byte[] body = "OK".getBytes(StandardCharsets.UTF_8);
			setCommonHeaders(exchange.getResponseHeaders());
			exchange.sendResponseHeaders(200, body.length);
			try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
		});

		// API
		server.createContext("/api", new ApiHandler());
		server.createContext("/api/events", this::handleEvents);
		server.createContext("/health/db", exchange -> {
			setCommonHeaders(exchange.getResponseHeaders());
			boolean ok = Db.testConnection();
			byte[] body = (ok?"OK":"NO_DB").getBytes(java.nio.charset.StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(ok?200:500, body.length);
			try (java.io.OutputStream os = exchange.getResponseBody()) { os.write(body); }
		});

		server.start();
		boolean dbMode = Db.isConfigured();
		Logger.info("DB mode: %s", dbMode ? "ON" : "OFF");

		// periodic reminders tick
		scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
		scheduler.scheduleAtFixedRate(this::tick, 10, 10, java.util.concurrent.TimeUnit.SECONDS);
		Logger.info("Scheduled reminders ticker started (every 10 seconds)");
	}

	public void stop() {
		Logger.info("Stopping HTTP server...");
		if (server != null) {
			server.stop(0);
			Logger.debug("HTTP server stopped");
		}
		if (executor != null) {
			executor.shutdownNow();
			Logger.debug("Executor service shut down");
		}
		if (scheduler != null) {
			scheduler.shutdownNow();
			Logger.debug("Scheduler shut down");
		}
	}

	private static void setCommonHeaders(Headers headers) {
		headers.set("Cache-Control", "no-store");
		headers.set("X-Content-Type-Options", "nosniff");
	}

	private static class StaticHandler implements HttpHandler {
		private final File root;

		StaticHandler(String rootDir) {
			this.root = new File(rootDir).getAbsoluteFile();
		}

		@Override
		public void handle(HttpExchange exchange) throws IOException {
			setCommonHeaders(exchange.getResponseHeaders());
			String path = exchange.getRequestURI().getPath();
			if (path.equals("/")) path = "/index.html";
			File file = new File(root, path);
			if (file.isDirectory()) file = new File(file, "index.html");
			if (!file.getCanonicalPath().startsWith(root.getCanonicalPath()) || !file.exists()) {
				byte[] nf = "Not Found".getBytes(StandardCharsets.UTF_8);
				exchange.sendResponseHeaders(404, nf.length);
				try (OutputStream os = exchange.getResponseBody()) { os.write(nf); }
				return;
			}
			String contentType = guessContentType(file.getName());
			exchange.getResponseHeaders().set("Content-Type", contentType);
			// Cache policy: HTML no-store; JS/CSS long cache (immutable); others modest default
			String lc = file.getName().toLowerCase();
			if (lc.endsWith(".html")) {
				exchange.getResponseHeaders().set("Cache-Control", "no-store");
			} else if (lc.endsWith(".js") || lc.endsWith(".css")) {
				exchange.getResponseHeaders().set("Cache-Control", "public, max-age=31536000, immutable");
			} else {
				exchange.getResponseHeaders().set("Cache-Control", "public, max-age=3600");
			}
			exchange.sendResponseHeaders(200, file.length());
			try (OutputStream os = exchange.getResponseBody(); InputStream is = new FileInputStream(file)) {
				is.transferTo(os);
			}
		}

		private static String guessContentType(String name) {
			String lc = name.toLowerCase();
			if (lc.endsWith(".html")) return "text/html; charset=utf-8";
			if (lc.endsWith(".js")) return "application/javascript; charset=utf-8";
			if (lc.endsWith(".css")) return "text/css; charset=utf-8";
			if (lc.endsWith(".json")) return "application/json; charset=utf-8";
			if (lc.endsWith(".png")) return "image/png";
			if (lc.endsWith(".jpg") || lc.endsWith(".jpeg")) return "image/jpeg";
			return "application/octet-stream";
		}
	}

	private class ApiHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			setCommonHeaders(exchange.getResponseHeaders());
			exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
			String method = exchange.getRequestMethod();
			String path = exchange.getRequestURI().getPath();
			String remoteAddr = exchange.getRemoteAddress() != null ? exchange.getRemoteAddress().getAddress().getHostAddress() : "unknown";
			Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
			String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			Map<String, String> form = method.equalsIgnoreCase("GET") ? new HashMap<>() : parseForm(body);

			int statusCode = 200;
			try {
				Logger.debug("API request: %s %s from %s", method, path, remoteAddr);
				if (path.equals("/api/register") && method.equals("POST")) { handleRegister(exchange, form); return; }
				if (path.equals("/api/login") && method.equals("POST")) { handleLogin(exchange, form); return; }
				if (path.equals("/api/logout") && method.equals("POST")) { handleLogout(exchange); return; }
				if (path.equals("/api/me") && method.equals("GET")) { handleMe(exchange); return; }
				if (path.equals("/api/transaction/add") && method.equals("POST")) { requireAuth(exchange); handleAddTx(exchange, form); return; }
				if (path.equals("/api/transactions") && method.equals("GET")) { requireAuth(exchange); handleListTx(exchange, query); return; }
				if (path.equals("/api/transaction/delete") && method.equals("POST")) { requireAuth(exchange); handleDeleteTx(exchange, form); return; }
				if (path.equals("/api/budget/set") && method.equals("POST")) { requireAuth(exchange); handleSetBudget(exchange, form); return; }
				if (path.equals("/api/budget") && method.equals("GET")) { requireAuth(exchange); handleGetBudget(exchange); return; }
				if (path.equals("/api/budget/delete") && method.equals("POST")) { requireAuth(exchange); handleDeleteBudget(exchange, form); return; }
				if (path.equals("/api/reports") && method.equals("GET")) { requireAuth(exchange); handleReports(exchange, query); return; }
				if (path.equals("/api/currency") && method.equals("GET")) { handleCurrency(exchange, query); return; }
				if (path.equals("/api/currency/convert") && method.equals("GET")) { handleCurrencyConvert(exchange, query); return; }
				if (path.equals("/api/currency/history") && method.equals("GET")) { handleCurrencyHistory(exchange, query); return; }
				if (path.equals("/api/recommendations") && method.equals("GET")) { requireAuth(exchange); handleRecommendations(exchange); return; }
				if (path.equals("/api/goal/set") && method.equals("POST")) { requireAuth(exchange); handleSetGoal(exchange, form); return; }
				if (path.equals("/api/reminder/add") && method.equals("POST")) { requireAuth(exchange); handleAddReminder(exchange, form); return; }
				if (path.equals("/api/reminders") && method.equals("GET")) { requireAuth(exchange); handleGetReminders(exchange); return; }
				if (path.equals("/api/categorizer/suggest") && method.equals("GET")) { requireAuth(exchange); handleSuggestCategory(exchange, query); return; }
				if (path.equals("/api/categorizer/add") && method.equals("POST")) { requireAuth(exchange); handleAddRule(exchange, form); return; }
				if (path.equals("/api/group/create") && method.equals("POST")) { requireAuth(exchange); handleGroupCreate(exchange, form); return; }
				if (path.equals("/api/group/join") && method.equals("POST")) { requireAuth(exchange); handleGroupJoin(exchange, form); return; }
				if (path.equals("/api/group/budget") && method.equals("GET")) { requireAuth(exchange); handleGroupBudget(exchange); return; }
				if (path.equals("/api/group/peers") && method.equals("GET")) { requireAuth(exchange); handleGroupPeers(exchange, query); return; }
				if (path.equals("/api/group/members") && method.equals("GET")) { requireAuth(exchange); handleGroupMembers(exchange); return; }
				if (path.equals("/api/group/leave") && method.equals("POST")) { requireAuth(exchange); handleGroupLeave(exchange); return; }
				if (path.equals("/api/group/me") && method.equals("GET")) { requireAuth(exchange); handleGroupMe(exchange); return; }
				if (path.equals("/api/credit/history") && method.equals("GET")) { requireAuth(exchange); handleCreditHistory(exchange); return; }
				statusCode = 404;
				writeJson(exchange, 404, jsonMsg("error","not_found"));
			} catch (UnauthorizedException ue) {
				statusCode = 401;
				Logger.warning("Unauthorized access attempt: %s %s from %s", method, path, remoteAddr);
				writeJson(exchange, 401, jsonMsg("error","unauthorized"));
			} catch (Exception e) {
				statusCode = 500;
				Logger.error("Error handling request %s %s from %s", e, method, path, remoteAddr);
				writeJson(exchange, 500, jsonMsg("error","server_error"));
			} finally {
				if (statusCode >= 400) {
					Logger.debug("API response: %s %s -> %d", method, path, statusCode);
				}
			}
		}

		private void handleRegister(HttpExchange ex, Map<String,String> form) throws IOException {
			String username = form.get("username");
			var u = auth.register(username, form.get("password"));
			if (u == null) {
				Logger.warning("Registration failed for username: %s", username);
				writeJson(ex, 400, jsonMsg("ok", false, "error", "user_exists_or_bad"));
				return;
			}
			Logger.info("User registered: %s (id: %s)", username, u.id);
			writeJson(ex, 200, jsonMsg("ok", true));
		}

		private void handleLogin(HttpExchange ex, Map<String,String> form) throws IOException {
			String username = form.get("username");
			var u = auth.login(username, form.get("password"));
			if (u == null) {
				Logger.warning("Login failed for username: %s", username);
				writeJson(ex, 400, jsonMsg("ok", false, "error", "bad_credentials"));
				return;
			}
			String sid = auth.createSession(u.username);
			ex.getResponseHeaders().add("Set-Cookie", "SID="+sid+"; HttpOnly; Path=/");
			Logger.info("User logged in: %s (id: %s, session: %s)", username, u.id, sid.substring(0, Math.min(8, sid.length())) + "...");
			String json = "{\"ok\":true,\"user\":{\"id\":\""+u.id+"\",\"username\":\""+escapeJson(u.username)+"\"}}";
			writeRaw(ex, 200, json);
		}

		private void handleLogout(HttpExchange ex) throws IOException {
			String sid = sessionId(ex);
			var u = user(ex);
			auth.destroySession(sid);
			ex.getResponseHeaders().add("Set-Cookie", "SID=; Max-Age=0; Path=/");
			if (u != null) {
				Logger.info("User logged out: %s", u.username);
			}
			writeJson(ex, 200, jsonMsg("ok", true));
		}

		private void handleMe(HttpExchange ex) throws IOException {
			var u = user(ex);
			if (u == null) { writeJson(ex, 200, jsonMsg("ok", false)); return; }
			String json = "{\"ok\":true,\"user\":{\"id\":\""+u.id+"\",\"username\":\""+escapeJson(u.username)+"\"}}";
			writeRaw(ex, 200, json);
		}

		private void handleAddTx(HttpExchange ex, Map<String,String> form) throws IOException {
			var u = requireAuth(ex);
			java.time.LocalDate date = java.time.LocalDate.parse(form.get("date"));
			double amount = Double.parseDouble(form.getOrDefault("amount","0"));
			String cat = emptyToNull(form.get("category"));
			String desc = emptyToNull(form.get("description"));
			if (cat == null) cat = categorizer.categorize(desc);
			Transaction t = finance.addTransaction(u.id, date, cat, desc, amount);
			Logger.info("Transaction added: user=%s, amount=%.2f, category=%s, date=%s", u.username, amount, cat, date);
			sse.send(sessionId(ex), "{\"type\":\"tx-added\"}");
			sse.send(sessionId(ex), "{\"type\":\"budget-update\"}");
			// Alerts: budget exceed
			if (cat != null) {
				for (Budget b : finance.getBudgets(u.id)) {
					if (b.category.equalsIgnoreCase(cat) && b.limit>0 && b.spent> b.limit) {
						Logger.warning("Budget exceeded: user=%s, category=%s, limit=%.2f, spent=%.2f", u.username, cat, b.limit, b.spent);
						sse.send(sessionId(ex), "{\"type\":\"alert\",\"message\":\"Превышен бюджет по категории '"+escapeJson(cat)+"'\"}");
						break;
					}
				}
			}
			// Anomaly detection
			if (finance.isAnomalousExpense(u.id, cat, amount)) {
				Logger.warning("Anomalous expense detected: user=%s, category=%s, amount=%.2f", u.username, cat, amount);
				sse.send(sessionId(ex), "{\"type\":\"alert\",\"message\":\"Аномально высокая трата\"}");
			}
			writeJson(ex, 200, jsonMsg("ok", true, "id", t.id));
		}

		private void handleDeleteTx(HttpExchange ex, Map<String,String> form) throws IOException {
			var u = requireAuth(ex);
			String id = form.get("id");
			if (id == null || id.isBlank()) { writeJson(ex, 400, jsonMsg("ok", false, "error","no_id")); return; }
			boolean ok = finance.deleteTransaction(u.id, id);
			if (ok) {
				sse.send(sessionId(ex), "{\"type\":\"budget-update\"}");
				writeJson(ex, 200, jsonMsg("ok", true));
			} else {
				writeJson(ex, 404, jsonMsg("ok", false, "error","not_found"));
			}
		}

		private void handleSuggestCategory(HttpExchange ex, Map<String,String> q) throws IOException {
			String desc = q.getOrDefault("desc", "");
			String cat = categorizer.categorize(desc);
			writeJson(ex, 200, "{\"category\":" + (cat==null?"null":"\""+escapeJson(cat)+"\"") + "}");
		}

		private void handleAddRule(HttpExchange ex, Map<String,String> form) throws IOException {
			String kw = form.get("keyword"); String cat = form.get("category");
			categorizer.addRule(kw, cat);
			writeJson(ex, 200, jsonMsg("ok", true));
		}

		private void handleGroupCreate(HttpExchange ex, Map<String,String> form) throws IOException {
			String name = form.getOrDefault("name","Семейный бюджет");
			var g = groups.create(name);
			writeJson(ex, 200, "{\"ok\":true,\"groupId\":\""+g.id+"\",\"name\":\""+escapeJson(g.name)+"\"}");
		}

		private void handleGroupJoin(HttpExchange ex, Map<String,String> form) throws IOException {
			var u = requireAuth(ex);
			String gid = form.get("groupId"); double share = Double.parseDouble(form.getOrDefault("share","1"));
			var g = groups.join(u.id, gid, share);
			if (g == null) { writeJson(ex, 400, jsonMsg("ok", false, "error","no_group")); return; }
			writeJson(ex, 200, jsonMsg("ok", true));
		}

		private void handleGroupBudget(HttpExchange ex) throws IOException {
			var u = requireAuth(ex);
			String gid = groups.userGroupId(u.id);
			if (gid == null) { writeJson(ex, 200, "{\"items\":[]}"); return; }
			var members = groups.members(gid);
			java.util.Map<String,double[]> agg = new java.util.HashMap<>();
			for (var entry : members.entrySet()) {
				String memberId = entry.getKey(); double share = entry.getValue();
				for (Budget b : finance.getBudgets(memberId)) {
					double[] arr = agg.computeIfAbsent(b.category, k-> new double[]{0,0});
					arr[0] += b.limit * share; arr[1] += b.spent * share;
				}
			}
			StringBuilder sb = new StringBuilder("{\"items\":["); int i=0;
			for (var e : agg.entrySet()) {
				if (i++>0) sb.append(',');
				sb.append("{\"category\":\"").append(escapeJson(e.getKey())).append("\",\"limit\":").append(FinanceService.round2(e.getValue()[0])).append(",\"spent\":").append(FinanceService.round2(e.getValue()[1])).append('}');
			}
			sb.append("]}");
			writeRaw(ex, 200, sb.toString());
		}

		private void handleGroupMembers(HttpExchange ex) throws IOException {
			var u = requireAuth(ex);
			String gid = groups.userGroupId(u.id);
			if (gid == null) { writeJson(ex, 200, "{\"items\":[]}"); return; }
			var members = groups.members(gid);
			StringBuilder sb = new StringBuilder("{\"items\":[");
			int i=0;
			for (var e : members.entrySet()) {
				if (i++>0) sb.append(',');
				sb.append("{\"userId\":\"").append(e.getKey()).append("\",\"share\":").append(FinanceService.round2(e.getValue())).append('}');
			}
			sb.append("]}");
			writeRaw(ex, 200, sb.toString());
		}

		private void handleGroupLeave(HttpExchange ex) throws IOException {
			var u = requireAuth(ex);
			groups.leave(u.id);
			writeJson(ex, 200, jsonMsg("ok", true));
		}

		private void handleGroupMe(HttpExchange ex) throws IOException {
			var u = requireAuth(ex);
			String gid = groups.userGroupId(u.id);
			if (gid == null) { writeJson(ex, 200, "{\"ok\":false}"); return; }
			Double share = groups.myShare(u.id);
			String json = "{\"ok\":true,\"groupId\":\""+gid+"\",\"share\":"+ (share==null?0.0:FinanceService.round2(share)) +"}";
			writeRaw(ex, 200, json);
		}

		private void handleGroupPeers(HttpExchange ex, Map<String,String> q) throws IOException {
			var u = requireAuth(ex);
			String gid = groups.userGroupId(u.id);
			if (gid == null) { writeJson(ex, 200, "{\"items\":[]}"); return; }
			java.time.LocalDate from = parseNullableDate(q.get("from"));
			java.time.LocalDate to = parseNullableDate(q.get("to"));
			var members = groups.members(gid);
			StringBuilder sb = new StringBuilder("{\"items\":[");
			int i=0;
			for (var e : members.entrySet()) {
				String memberId = e.getKey();
				var list = finance.listTransactions(memberId, from, to, null);
				double income = 0.0, expense = 0.0;
				for (var t : list) {
					if (t.amount > 0) income += t.amount;
					else expense += -t.amount;
				}
				if (i++>0) sb.append(',');
				sb.append("{\"userId\":\"").append(memberId).append("\",\"income\":").append(FinanceService.round2(income)).append(",\"expense\":").append(FinanceService.round2(expense)).append('}');
			}
			sb.append("]}");
			writeRaw(ex, 200, sb.toString());
		}

		private void handleCreditHistory(HttpExchange ex) throws IOException {
			// stub demo data
			String json = "{\"items\":[{\"date\":\"2024-12-01\",\"score\":\"A-\",\"summary\":\"Нет просрочек\"},{\"date\":\"2025-06-01\",\"score\":\"A\",\"summary\":\"История улучшена\"}]}";
			writeRaw(ex, 200, json);
		}

		private void handleListTx(HttpExchange ex, Map<String,String> query) throws IOException {
			var u = requireAuth(ex);
			java.time.LocalDate from = parseNullableDate(query.get("from"));
			java.time.LocalDate to = parseNullableDate(query.get("to"));
			String cat = emptyToNull(query.get("category"));
			java.util.List<Transaction> list = finance.listTransactions(u.id, from, to, cat);
			StringBuilder sb = new StringBuilder();
			sb.append("{\"items\":[");
			for (int i=0;i<list.size();i++) {
				Transaction t = list.get(i);
				if (i>0) sb.append(',');
				sb.append("{\"id\":\"").append(t.id).append("\",\"date\":\"")
						.append(java.time.LocalDate.ofEpochDay(t.dateEpochDay)).append("\",\"category\":");
				sb.append(t.category==null?"null":"\""+escapeJson(t.category)+"\"");
				sb.append(",\"description\":").append(t.description==null?"null":"\""+escapeJson(t.description)+"\"");
				sb.append(",\"amount\":").append(FinanceService.round2(t.amount)).append('}');
			}
			sb.append("]}");
			writeRaw(ex, 200, sb.toString());
		}

		private void handleSetBudget(HttpExchange ex, Map<String,String> form) throws IOException {
			var u = requireAuth(ex);
			String category = form.get("category");
			double limit = Double.parseDouble(form.getOrDefault("limit","0"));
			Budget b = finance.setBudget(u.id, category, limit);
			sse.send(sessionId(ex), "{\"type\":\"budget-update\"}");
			writeJson(ex, 200, jsonMsg("ok", true, "category", category, "limit", b.limit));
		}

		private void handleDeleteBudget(HttpExchange ex, Map<String,String> form) throws IOException {
			var u = requireAuth(ex);
			String category = form.get("category");
			if (category == null || category.isBlank()) { writeJson(ex, 400, jsonMsg("ok", false, "error","no_category")); return; }
			finance.deleteBudget(u.id, category);
			sse.send(sessionId(ex), "{\"type\":\"budget-update\"}");
			writeJson(ex, 200, jsonMsg("ok", true));
		}

		private void handleGetBudget(HttpExchange ex) throws IOException {
			var u = requireAuth(ex);
			java.util.List<Budget> list = finance.getBudgets(u.id);
			StringBuilder sb = new StringBuilder();
			sb.append("{\"items\":[");
			for (int i=0;i<list.size();i++) {
				Budget b = list.get(i);
				if (i>0) sb.append(',');
				sb.append("{\"category\":\"").append(escapeJson(b.category)).append("\",\"limit\":").append(FinanceService.round2(b.limit)).append(",\"spent\":").append(FinanceService.round2(b.spent)).append('}');
			}
			sb.append("]}");
			writeRaw(ex, 200, sb.toString());
		}

		private void handleReports(HttpExchange ex, Map<String,String> q) throws IOException {
			var u = requireAuth(ex);
			java.time.LocalDate from = java.time.LocalDate.now().withDayOfMonth(1);
			java.time.LocalDate to = java.time.LocalDate.now();
			var items = finance.reportByCategory(u.id, from, to);
			StringBuilder sb = new StringBuilder("{\"items\":[");
			for (int i=0;i<items.size();i++) {
				var m = items.get(i);
				if (i>0) sb.append(',');
				sb.append("{\"category\":\"").append(escapeJson((String)m.get("category"))).append("\",\"total\":").append(m.get("total")).append('}');
			}
			sb.append("]}");
			writeRaw(ex, 200, sb.toString());
		}

		private void handleCurrency(HttpExchange ex, Map<String,String> q) throws IOException {
			CurrencyRates r = currency.getRates();
			String base = q.getOrDefault("base", "MDL");
			String symbols = q.get("symbols");
			java.util.Map<String, Double> mdlMap = new java.util.HashMap<>(r.rates);
			mdlMap.put("MDL", 1.0);
			java.util.List<String> list = symbols==null||symbols.isBlank()? java.util.List.of("USD","EUR","RUB","MDL") : java.util.Arrays.asList(symbols.split(","));
			StringBuilder sb = new StringBuilder("{\"base\":\"").append(base).append("\",\"rates\":{");
			int i=0;
			for (String sym : list) {
				if (!mdlMap.containsKey(sym)) continue;
				double rate;
				if (base.equals("MDL")) {
					rate = mdlMap.get(sym);
				} else {
					Double b = mdlMap.get(base);
					if (b == null || b == 0.0) continue;
					rate = mdlMap.get(sym) / b;
				}
				if (i++>0) sb.append(',');
				sb.append("\"").append(sym).append("\":").append(FinanceService.round2(rate));
			}
			sb.append("}}");
			writeRaw(ex, 200, sb.toString());
		}

		private void handleCurrencyConvert(HttpExchange ex, Map<String,String> q) throws IOException {
			String from = q.getOrDefault("from", "USD");
			String to = q.getOrDefault("to", "MDL");
			double amount = 0.0;
			try { amount = Double.parseDouble(q.getOrDefault("amount","0")); } catch (Exception ignored) {}
			String date = q.get("date");
			double res = currency.convert(from, to, amount, date);
			if (Double.isNaN(res)) { writeJson(ex, 400, jsonMsg("ok", false, "error","bad_currency")); return; }
			String json = "{\"ok\":true,\"from\":\""+from.toUpperCase()+"\",\"to\":\""+to.toUpperCase()+"\",\"amount\":"+FinanceService.round2(amount)+",\"result\":"+FinanceService.round2(res)+"}";
			writeRaw(ex, 200, json);
		}

		private void handleCurrencyHistory(HttpExchange ex, Map<String,String> q) throws IOException {
			String base = q.getOrDefault("base","MDL").toUpperCase();
			String symbols = q.getOrDefault("symbols","USD").toUpperCase();
			String date = q.get("date");
			String from = q.get("from");
			String to = q.get("to");
			// sanitize dates
			java.time.LocalDate today = java.time.LocalDate.now();
			if (from != null && !from.isBlank()) { try { java.time.LocalDate.parse(from); } catch (Exception e) { from = null; } }
			if (to != null && !to.isBlank()) { try { java.time.LocalDate.parse(to); } catch (Exception e) { to = null; } }
			if (to != null && java.time.LocalDate.parse(to).isAfter(today)) to = today.toString();
			if (from != null && to != null) {
				java.time.LocalDate f = java.time.LocalDate.parse(from), t = java.time.LocalDate.parse(to);
				if (f.isAfter(t)) { String tmp = from; from = to; to = tmp; }
			}
			if (from != null && to != null) {
				var map = currency.historyRange(base, symbols.split(",")[0], from, to);
				if (map == null) { writeJson(ex, 500, jsonMsg("ok", false, "error","history_unavailable")); return; }
				StringBuilder sb = new StringBuilder("{\"base\":\"").append(base).append("\",\"symbol\":\"").append(symbols.split(",")[0]).append("\",\"rates\":{");
				int i=0;
				for (var e : new java.util.TreeMap<>(map).entrySet()) { // date -> {sym:rate}
					if (i++>0) sb.append(',');
					sb.append("\"").append(e.getKey()).append("\":").append(e.getValue().get(symbols.split(",")[0]));
				}
				sb.append("}}");
				writeRaw(ex, 200, sb.toString());
				return;
			}
			if (date != null) {
				var m = currency.historyOnDate(base, java.util.Arrays.asList(symbols.split(",")), date);
				if (m == null) { writeJson(ex, 500, jsonMsg("ok", false, "error","history_unavailable")); return; }
				StringBuilder sb = new StringBuilder("{\"base\":\"").append(base).append("\",\"date\":\"").append(date).append("\",\"rates\":{");
				int i=0; for (String s : symbols.split(",")) {
					Double v = m.get(s);
					if (v == null) continue;
					if (i++>0) sb.append(',');
					sb.append("\"").append(s).append("\":").append(FinanceService.round2(v));
				}
				sb.append("}}");
				writeRaw(ex, 200, sb.toString());
				return;
			}
			writeJson(ex, 400, jsonMsg("ok", false, "error","need_date_or_range"));
		}

		private void handleRecommendations(HttpExchange ex) throws IOException {
			var u = requireAuth(ex);
			var recs = finance.recommendations(u.id);
			StringBuilder sb = new StringBuilder("{\"items\":[");
			for (int i=0;i<recs.size();i++) {
				var m = recs.get(i);
				if (i>0) sb.append(',');
				sb.append("{\"message\":\"").append(escapeJson((String)m.get("message"))).append("\",\"potentialSave\":").append(m.get("potentialSave")).append('}');
			}
			sb.append("]}");
			writeRaw(ex, 200, sb.toString());
		}

		private void handleSetGoal(HttpExchange ex, Map<String,String> form) throws IOException {
			var u = requireAuth(ex);
			String name = form.getOrDefault("name","Цель");
			double target = Double.parseDouble(form.getOrDefault("targetAmount","0"));
			double current = Double.parseDouble(form.getOrDefault("currentAmount","0"));
			Goal g = finance.setGoal(u.id, name, target, current);
			sse.send(sessionId(ex), "{\"type\":\"goal-update\"}");
			writeJson(ex, 200, jsonMsg("ok", true));
		}

		private void handleAddReminder(HttpExchange ex, Map<String,String> form) throws IOException {
			var u = requireAuth(ex);
			java.time.LocalDate due = java.time.LocalDate.parse(form.get("dueDate"));
			String msg = form.getOrDefault("message", "Платёж");
			Double amount = form.containsKey("amount") && !form.get("amount").isBlank() ? Double.parseDouble(form.get("amount")) : null;
			finance.addReminder(u.id, due, msg, amount);
			writeJson(ex, 200, jsonMsg("ok", true));
		}

		private void handleGetReminders(HttpExchange ex) throws IOException {
			var u = requireAuth(ex);
			var list = finance.getReminders(u.id);
			StringBuilder sb = new StringBuilder("{\"items\":[");
			for (int i=0;i<list.size();i++) {
				var r = list.get(i);
				if (i>0) sb.append(',');
				sb.append("{\"id\":\"").append(r.id).append("\",\"date\":\"")
						.append(java.time.LocalDate.ofEpochDay(r.dueEpochDay)).append("\",\"message\":\"")
						.append(escapeJson(r.message)).append("\",\"amount\":")
						.append(r.amount==null?"null":FinanceService.round2(r.amount)).append("}");
			}
			sb.append("]}");
			writeRaw(ex, 200, sb.toString());
		}

		private String sessionId(HttpExchange ex) {
			java.util.List<String> cookies = ex.getRequestHeaders().get("Cookie");
			if (cookies == null) return null;
			for (String c : cookies) {
				for (String part : c.split(";")) {
					String[] kv = part.trim().split("=");
					if (kv.length==2 && kv[0].equals("SID")) return kv[1];
				}
			}
			return null;
		}

		private com.acs.finance.model.User user(HttpExchange ex) {
			String sid = sessionId(ex);
			if (sid == null) return null;
			return auth.getUserBySession(sid);
		}

		private com.acs.finance.model.User requireAuth(HttpExchange ex) {
			com.acs.finance.model.User u = user(ex);
			if (u == null) throw new UnauthorizedException();
			return u;
		}

		private void writeJson(HttpExchange ex, int code, String json) throws IOException { writeRaw(ex, code, json); }
		private void writeRaw(HttpExchange ex, int code, String json) throws IOException {
			byte[] b = json.getBytes(StandardCharsets.UTF_8);
			ex.sendResponseHeaders(code, b.length);
			try (OutputStream os = ex.getResponseBody()) { os.write(b); }
		}

		private static Map<String, String> parseQuery(String raw) {
			Map<String, String> map = new HashMap<>();
			if (raw == null || raw.isEmpty()) return map;
			for (String p : raw.split("&")) {
				int i = p.indexOf('=');
				if (i > 0) {
					String k = URLDecoder.decode(p.substring(0, i), StandardCharsets.UTF_8);
					String v = URLDecoder.decode(p.substring(i + 1), StandardCharsets.UTF_8);
					map.put(k, v);
				} else {
					map.put(URLDecoder.decode(p, StandardCharsets.UTF_8), "");
				}
			}
			return map;
		}

		private static Map<String, String> parseForm(String body) {
			Map<String, String> map = new HashMap<>();
			if (body == null || body.isEmpty()) return map;
			for (String p : body.split("&")) {
				int i = p.indexOf('=');
				if (i > 0) {
					String k = URLDecoder.decode(p.substring(0, i), StandardCharsets.UTF_8);
					String v = URLDecoder.decode(p.substring(i + 1), StandardCharsets.UTF_8);
					map.put(k, v);
				}
			}
			return map;
		}

		private static String escapeJson(String s) {
			return s.replace("\\", "\\\\").replace("\"", "\\\"");
		}

		private java.time.LocalDate parseNullableDate(String s) { return (s==null||s.isBlank())?null:java.time.LocalDate.parse(s); }
		private String emptyToNull(String s) { return (s==null||s.isBlank())?null:s; }
	}

	private void handleEvents(HttpExchange ex) throws IOException {
		setCommonHeaders(ex.getResponseHeaders());
		ex.getResponseHeaders().set("Cache-Control", "no-store");
		String sid = new ApiHandler().sessionId(ex);
		var u = sid != null ? auth.getUserBySession(sid) : null;
		if (sid == null || u == null) {
			Logger.warning("SSE connection rejected: unauthorized");
			ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
			byte[] b = jsonMsg("error","unauthorized").getBytes(StandardCharsets.UTF_8);
			ex.sendResponseHeaders(401, b.length);
			try (OutputStream os = ex.getResponseBody()) { os.write(b); }
			return;
		}
		try {
			sse.register(sid, ex);
			Logger.info("SSE connection established: user=%s, session=%s", u.username, sid.substring(0, Math.min(8, sid.length())) + "...");
		} catch (IOException e) {
			Logger.error("Failed to register SSE connection", e);
		}
	}

	private void tick() {
		long today = java.time.LocalDate.now().toEpochDay();
		int remindersSent = 0;
		for (String sid : sse.sessionIds()) {
			var u = auth.getUserBySession(sid);
			if (u == null) continue;
			var due = finance.consumeDueReminders(u.id, today);
			for (var r : due) {
				String msg = r.message + (r.amount!=null? (" ("+FinanceService.round2(r.amount)+")") : "");
				sse.send(sid, "{\"type\":\"reminder\",\"message\":\""+escapeJson(msg)+"\"}");
				remindersSent++;
				Logger.debug("Reminder sent: user=%s, message=%s", u.username, r.message);
			}
		}
		if (remindersSent > 0) {
			Logger.info("Reminders tick: %d reminders sent", remindersSent);
		}
	}

	private static String escapeJson(String s) {
		return s.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private static String jsonMsg(Object... kv) {
		StringBuilder sb = new StringBuilder("{");
		for (int i=0;i<kv.length;i+=2) {
			if (i>0) sb.append(',');
			sb.append("\"").append(kv[i]).append("\":");
			Object v = kv[i+1];
			if (v instanceof Boolean || v instanceof Number) sb.append(v);
			else sb.append("\"").append(v).append("\"");
		}
		sb.append('}');
		return sb.toString();
	}

	private static class UnauthorizedException extends RuntimeException {}
}


