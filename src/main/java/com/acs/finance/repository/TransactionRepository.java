package com.acs.finance.repository;

import com.acs.finance.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String> {
    
    List<Transaction> findByUserIdOrderByDateEpochDayAsc(String userId);
    
    @Query("SELECT t FROM Transaction t WHERE t.userId = :userId " +
           "AND (:from IS NULL OR t.dateEpochDay >= :from) " +
           "AND (:to IS NULL OR t.dateEpochDay <= :to) " +
           "AND (:category IS NULL OR LOWER(t.category) = LOWER(:category)) " +
           "ORDER BY t.dateEpochDay ASC")
    List<Transaction> findFiltered(
        @Param("userId") String userId,
        @Param("from") Long from,
        @Param("to") Long to,
        @Param("category") String category
    );
    
    void deleteByIdAndUserId(String id, String userId);
    
    boolean existsByIdAndUserId(String id, String userId);
}
