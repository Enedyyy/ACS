package com.acs.finance.model;

public class Goal {
	public final String userId;
	public final String name;
	public volatile double targetAmount;
	public volatile double currentAmount;

	public Goal(String userId, String name, double targetAmount, double currentAmount) {
		this.userId = userId;
		this.name = name;
		this.targetAmount = targetAmount;
		this.currentAmount = currentAmount;
	}
}


