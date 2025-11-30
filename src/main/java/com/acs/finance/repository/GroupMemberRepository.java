package com.acs.finance.repository;

import com.acs.finance.entity.GroupMember;
import com.acs.finance.entity.GroupMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMember, GroupMemberId> {
    
    @Query("SELECT gm FROM GroupMember gm WHERE gm.id.groupId = :groupId")
    List<GroupMember> findByGroupId(@Param("groupId") String groupId);
    
    @Query("SELECT gm FROM GroupMember gm WHERE gm.id.userId = :userId")
    Optional<GroupMember> findByUserId(@Param("userId") String userId);
    
    @Query("SELECT gm.id.groupId FROM GroupMember gm WHERE gm.id.userId = :userId")
    Optional<String> findGroupIdByUserId(@Param("userId") String userId);
    
    @Modifying
    @Query("DELETE FROM GroupMember gm WHERE gm.id.userId = :userId")
    void deleteByUserId(@Param("userId") String userId);
    
    @Modifying
    @Query("SELECT COUNT(gm) FROM GroupMember gm WHERE gm.id.groupId = :groupId")
    int countByGroupId(@Param("groupId") String groupId);
}
