
package com.acs.finance.service;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SseHub {
	private static class Client {
		final HttpExchange exchange;
		final OutputStream os;
		Client(HttpExchange e) throws IOException { this.exchange = e; this.os = e.getResponseBody(); }
	}

	private final Map<String, Client> bySession = new ConcurrentHashMap<>();

	public void register(String sessionId, HttpExchange exchange) throws IOException {
		Headers h = exchange.getResponseHeaders();
		h.set("Content-Type", "text/event-stream; charset=utf-8");
		h.set("Cache-Control", "no-store");
		h.set("Connection", "keep-alive");
		exchange.sendResponseHeaders(200, 0);
		Client c = new Client(exchange);
		bySession.put(sessionId, c);
		// initial ping
		send(sessionId, "{\"type\":\"hello\"}");
	}

	public void unregister(String sessionId) {
		Client c = bySession.remove(sessionId);
		if (c != null) try { c.os.close(); } catch (Exception ignored) {}
	}

	public void send(String sessionId, String json) {
		Client c = bySession.get(sessionId);
		if (c == null) return;
		try {
			String data = "data: " + json + "\n\n";
			c.os.write(data.getBytes(StandardCharsets.UTF_8));
			c.os.flush();
		} catch (Exception e) {
			unregister(sessionId);
		}
	}

	public void broadcast(Set<String> sessionIds, String json) {
		for (String sid : sessionIds) send(sid, json);
	}

	public Set<String> sessionIds() { return new java.util.HashSet<>(bySession.keySet()); }
}


