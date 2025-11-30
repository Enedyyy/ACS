package com.acs.finance.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "reminders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reminder {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "due_epoch_day", nullable = false)
    private Long dueEpochDay;

    @Column(nullable = false)
    private String message;

    @Column(precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private Boolean sent = false;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (sent == null) {
            sent = false;
        }
    }

    // Convenience method
    public Double getAmountAsDouble() {
        return amount != null ? amount.doubleValue() : null;
    }
}
