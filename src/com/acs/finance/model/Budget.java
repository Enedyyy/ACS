package com.acs.finance.model;

public class Budget {
	public final String userId;
	public final String category;
	public volatile double limit;
	public volatile double spent;

	public Budget(String userId, String category, double limit) {
		this.userId = userId;
		this.category = category;
		this.limit = limit;
		this.spent = 0.0;
	}
}


