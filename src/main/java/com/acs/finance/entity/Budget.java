package com.acs.finance.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "budgets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Budget {

    @EmbeddedId
    private BudgetId id;

    @Column(name = "limit_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal limitAmount;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal spent = BigDecimal.ZERO;

    // Convenience constructor
    public Budget(String userId, String category, double limit) {
        this.id = new BudgetId(userId, category);
        this.limitAmount = BigDecimal.valueOf(limit);
        this.spent = BigDecimal.ZERO;
    }

    // Convenience getters
    public String getUserId() {
        return id != null ? id.getUserId() : null;
    }

    public String getCategory() {
        return id != null ? id.getCategory() : null;
    }

    public double getLimitAsDouble() {
        return limitAmount != null ? limitAmount.doubleValue() : 0.0;
    }

    public double getSpentAsDouble() {
        return spent != null ? spent.doubleValue() : 0.0;
    }

    public void addSpent(double amount) {
        if (spent == null) spent = BigDecimal.ZERO;
        spent = spent.add(BigDecimal.valueOf(amount));
    }
}
