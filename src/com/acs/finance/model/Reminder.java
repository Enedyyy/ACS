package com.acs.finance.model;

public class Reminder {
	public final String id;
	public final String userId;
	public final long dueEpochDay;
	public final String message;
	public final Double amount; // nullable
	public volatile boolean sent;

	public Reminder(String id, String userId, long dueEpochDay, String message, Double amount) {
		this.id = id;
		this.userId = userId;
		this.dueEpochDay = dueEpochDay;
		this.message = message;
		this.amount = amount;
		this.sent = false;
	}
}


