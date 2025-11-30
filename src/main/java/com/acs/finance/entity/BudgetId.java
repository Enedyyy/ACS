package com.acs.finance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;
import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BudgetId implements Serializable {

    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(length = 100)
    private String category;
}
