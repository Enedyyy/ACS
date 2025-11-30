package com.acs.finance.service;

import com.acs.finance.entity.Group;
import com.acs.finance.entity.GroupMember;
import com.acs.finance.entity.GroupMemberId;
import com.acs.finance.repository.GroupMemberRepository;
import com.acs.finance.repository.GroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;

    @Transactional
    public Group create(String name) {
        Group group = new Group();
        group.setName(name != null ? name : "Группа");
        return groupRepository.save(group);
    }

    @Transactional
    public Group join(String userId, String groupId, double share) {
        // Check if user is already in another group
        Optional<String> currentGroupId = groupMemberRepository.findGroupIdByUserId(userId);
        if (currentGroupId.isPresent() && !currentGroupId.get().equals(groupId)) {
            // Leave current group first
            leave(userId);
        }
        
        // Check if group exists
        Optional<Group> groupOpt = groupRepository.findById(groupId);
        if (groupOpt.isEmpty()) {
            return null;
        }
        
        Group group = groupOpt.get();
        
        // Add or update member
        double clampedShare = Math.max(0.0, Math.min(1.0, share));
        GroupMemberId memberId = new GroupMemberId(groupId, userId);
        GroupMember member = new GroupMember();
        member.setId(memberId);
        member.setShare(BigDecimal.valueOf(clampedShare));
        groupMemberRepository.save(member);
        
        return group;
    }

    public String userGroupId(String userId) {
        return groupMemberRepository.findGroupIdByUserId(userId).orElse(null);
    }

    public Group get(String id) {
        return groupRepository.findById(id).orElse(null);
    }

    public Map<String, Double> members(String groupId) {
        List<GroupMember> members = groupMemberRepository.findByGroupId(groupId);
        Map<String, Double> result = new LinkedHashMap<>();
        for (GroupMember m : members) {
            result.put(m.getUserId(), m.getShareAsDouble());
        }
        return result;
    }

    @Transactional
    public void leave(String userId) {
        String groupId = userGroupId(userId);
        if (groupId == null) {
            return;
        }
        
        groupMemberRepository.deleteByUserId(userId);
        
        int count = groupMemberRepository.countByGroupId(groupId);
        if (count == 0) {
            try {
                groupRepository.deleteById(groupId);
            } catch (Exception e) {
                log.error("Failed to delete empty group: {}", groupId, e);
            }
        }
    }

    public Double myShare(String userId) {
        Optional<GroupMember> member = groupMemberRepository.findByUserId(userId);
        return member.map(GroupMember::getShareAsDouble).orElse(null);
    }

    public String getName(String groupId) {
        Optional<Group> group = groupRepository.findById(groupId);
        return group.map(Group::getName).orElse(null);
    }
}
