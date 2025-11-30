package com.acs.finance.scheduler;

import com.acs.finance.entity.Reminder;
import com.acs.finance.entity.User;
import com.acs.finance.service.AuthService;
import com.acs.finance.service.FinanceService;
import com.acs.finance.service.SseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReminderScheduler {

    private final AuthService authService;
    private final FinanceService financeService;
    private final SseService sseService;

    @Scheduled(fixedRate = 10000) // Every 10 seconds
    public void checkReminders() {
        long today = LocalDate.now().toEpochDay();
        int remindersSent = 0;
        
        Set<String> sessions = sseService.sessionIds();
        for (String sid : sessions) {
            User user = authService.getUserBySession(sid);
            if (user == null) continue;
            
            List<Reminder> dueReminders = financeService.consumeDueReminders(user.getId(), today);
            
            for (Reminder r : dueReminders) {
                String msg = r.getMessage();
                if (r.getAmountAsDouble() != null) {
                    msg += " (" + FinanceService.round2(r.getAmountAsDouble()) + ")";
                }
                
                String escapedMsg = msg.replace("\\", "\\\\").replace("\"", "\\\"");
                sseService.send(sid, "{\"type\":\"reminder\",\"message\":\"" + escapedMsg + "\"}");
                remindersSent++;
            }
        }
        
        if (remindersSent > 0) {
            log.info("Reminders tick: {} reminders sent", remindersSent);
        }
    }
}
