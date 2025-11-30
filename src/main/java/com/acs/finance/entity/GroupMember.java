package com.acs.finance.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "group_members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GroupMember {

    @EmbeddedId
    private GroupMemberId id;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal share;

    // Convenience constructor
    public GroupMember(String groupId, String userId, double share) {
        this.id = new GroupMemberId(groupId, userId);
        this.share = BigDecimal.valueOf(share);
    }

    // Convenience getters
    public String getGroupId() {
        return id != null ? id.getGroupId() : null;
    }

    public String getUserId() {
        return id != null ? id.getUserId() : null;
    }

    public double getShareAsDouble() {
        return share != null ? share.doubleValue() : 1.0;
    }
}
