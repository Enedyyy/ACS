package com.acs.finance.repository;

import com.acs.finance.entity.Reminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReminderRepository extends JpaRepository<Reminder, String> {
    
    @Query("SELECT r FROM Reminder r WHERE r.userId = :userId ORDER BY r.dueEpochDay")
    List<Reminder> findByUserId(@Param("userId") String userId);
    
    @Query("SELECT r FROM Reminder r WHERE r.userId = :userId AND r.sent = false AND r.dueEpochDay <= :today")
    List<Reminder> findDueReminders(@Param("userId") String userId, @Param("today") long today);
    
    @Modifying
    @Query("UPDATE Reminder r SET r.sent = true WHERE r.id = :id")
    void markAsSent(@Param("id") String id);
}
