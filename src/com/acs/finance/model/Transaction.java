package com.acs.finance.model;

public class Transaction {
	public final String id;
	public final String userId;
	public final long dateEpochDay; // yyyy-MM-dd as days since epoch
	public final String category; // nullable
	public final String description; // nullable
	public final double amount; // negative for expense, positive for income

	public Transaction(String id, String userId, long dateEpochDay, String category, String description, double amount) {
		this.id = id;
		this.userId = userId;
		this.dateEpochDay = dateEpochDay;
		this.category = category;
		this.description = description;
		this.amount = amount;
	}
}


