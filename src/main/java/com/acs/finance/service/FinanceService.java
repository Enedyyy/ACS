package com.acs.finance.service;

import com.acs.finance.entity.Budget;
import com.acs.finance.entity.BudgetId;
import com.acs.finance.entity.Reminder;
import com.acs.finance.entity.Transaction;
import com.acs.finance.repository.BudgetRepository;
import com.acs.finance.repository.ReminderRepository;
import com.acs.finance.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinanceService {

    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final ReminderRepository reminderRepository;

    @Transactional
    public Transaction addTransaction(String userId, LocalDate date, String category, String description, double amount) {
        Transaction transaction = Transaction.builder()
                .userId(userId)
                .dateEpochDay(date.toEpochDay())
                .category(category)
                .description(description)
                .amount(BigDecimal.valueOf(amount))
                .build();
        
        transaction = transactionRepository.save(transaction);
        
        // Update budget spent if expense with category
        if (category != null && amount < 0) {
            try {
                budgetRepository.addSpent(userId, category, BigDecimal.valueOf(-amount));
            } catch (Exception e) {
                log.error("Failed to update budget spent", e);
            }
        }
        
        return transaction;
    }

    public List<Transaction> listTransactions(String userId, LocalDate from, LocalDate to, String category) {
        Long fromEpoch = from != null ? from.toEpochDay() : null;
        Long toEpoch = to != null ? to.toEpochDay() : null;
        String cat = (category != null && !category.isBlank()) ? category : null;
        
        return transactionRepository.findFiltered(userId, fromEpoch, toEpoch, cat);
    }

    @Transactional
    public boolean deleteTransaction(String userId, String txId) {
        Optional<Transaction> found = transactionRepository.findById(txId);
        
        if (found.isEmpty() || !found.get().getUserId().equals(userId)) {
            log.warn("Transaction not found for deletion: txId={}, userId={}", txId, userId);
            return false;
        }
        
        Transaction tx = found.get();
        transactionRepository.delete(tx);
        
        // Adjust budget if needed
        if (tx.getAmountAsDouble() < 0 && tx.getCategory() != null) {
            try {
                budgetRepository.addSpent(userId, tx.getCategory(), BigDecimal.valueOf(tx.getAmountAsDouble()));
            } catch (Exception e) {
                log.error("Failed to update budget after transaction deletion", e);
            }
        }
        
        return true;
    }

    @Transactional
    public Budget setBudget(String userId, String category, double limit) {
        BudgetId id = new BudgetId(userId, category);
        Optional<Budget> existing = budgetRepository.findById(id);
        
        Budget budget;
        if (existing.isPresent()) {
            budget = existing.get();
            budget.setLimitAmount(BigDecimal.valueOf(limit));
        } else {
            budget = new Budget(userId, category, limit);
        }
        
        return budgetRepository.save(budget);
    }

    public List<Budget> getBudgets(String userId) {
        return budgetRepository.findByUserId(userId);
    }

    @Transactional
    public void deleteBudget(String userId, String category) {
        budgetRepository.deleteByUserIdAndCategory(userId, category);
    }

    @Transactional
    public Reminder addReminder(String userId, LocalDate due, String message, Double amount) {
        Reminder reminder = Reminder.builder()
                .userId(userId)
                .dueEpochDay(due.toEpochDay())
                .message(message)
                .amount(amount != null ? BigDecimal.valueOf(amount) : null)
                .sent(false)
                .build();
        
        return reminderRepository.save(reminder);
    }

    public List<Reminder> getReminders(String userId) {
        return reminderRepository.findByUserId(userId);
    }

    @Transactional
    public List<Reminder> consumeDueReminders(String userId, long todayEpochDay) {
        List<Reminder> dueReminders = reminderRepository.findDueReminders(userId, todayEpochDay);
        
        for (Reminder r : dueReminders) {
            reminderRepository.markAsSent(r.getId());
        }
        
        return dueReminders;
    }

    public static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
