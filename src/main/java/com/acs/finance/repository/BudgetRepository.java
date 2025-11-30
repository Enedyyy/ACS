package com.acs.finance.repository;

import com.acs.finance.entity.Budget;
import com.acs.finance.entity.BudgetId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, BudgetId> {
    
    @Query("SELECT b FROM Budget b WHERE b.id.userId = :userId ORDER BY b.id.category")
    List<Budget> findByUserId(@Param("userId") String userId);
    
    @Query("SELECT b FROM Budget b WHERE b.id.userId = :userId AND b.id.category = :category")
    Optional<Budget> findByUserIdAndCategory(@Param("userId") String userId, @Param("category") String category);
    
    @Modifying
    @Query("UPDATE Budget b SET b.spent = b.spent + :amount WHERE b.id.userId = :userId AND LOWER(b.id.category) = LOWER(:category)")
    int addSpent(@Param("userId") String userId, @Param("category") String category, @Param("amount") BigDecimal amount);
    
    @Modifying
    @Query("DELETE FROM Budget b WHERE b.id.userId = :userId AND b.id.category = :category")
    void deleteByUserIdAndCategory(@Param("userId") String userId, @Param("category") String category);
}
